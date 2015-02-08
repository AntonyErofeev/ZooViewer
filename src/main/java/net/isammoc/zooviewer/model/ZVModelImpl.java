/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.isammoc.zooviewer.model;

import net.isammoc.zooviewer.node.ZVNode;
import net.isammoc.zooviewer.node.ZVNodeImpl;
import org.apache.zookeeper.AsyncCallback.ChildrenCallback;
import org.apache.zookeeper.*;
import org.apache.zookeeper.common.PathUtils;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.event.EventListenerList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;

/**
 * Implementation of the ZooViewer model.
 * 
 * @author franck
 */
public class ZVModelImpl implements ZVModel {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final EventListenerList listenerList = new EventListenerList();

    private final ZooKeeper zk;

    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor();

    private final Map<String, ZVNodeImpl> nodes = new HashMap<>();

    private final Map<ZVNodeImpl, List<ZVNodeImpl>> children = new HashMap<>();

    private final ZkWatcher watcher;

    private final class ZkWatcher implements Watcher {
        private final Object lock = new Object();
        private volatile boolean dead = true;

        @Override
        public void process(WatchedEvent event) {
            log.info("[{}] event : {}", Thread.currentThread(), event);
            switch (event.getType()) {
                case None:
                    switch (event.getState()) {
                        case Disconnected:
                        case Expired:
                            log.info("[{}] Session has expired", Thread.currentThread());
                            synchronized (lock) {
                                dead = true;
                                lock.notifyAll();
                            }
                            break;
                        case SyncConnected:
                            log.info("[{}] Connected to the server", Thread.currentThread());
                            synchronized (lock) {
                                dead = false;
                                lock.notifyAll();
                            }
                            break;
                    }
                    zk.register(this);
                    break;
                case NodeCreated:
                    log.info("Node {} created", event.getPath());
                    break;
                case NodeChildrenChanged:
                    log.info("Children changed for node {}", event.getPath());
                    populateChildren(event.getPath());
                    break;
                case NodeDeleted:
                    log.info("Node {} deleted", event.getPath());
                    nodeDeleted(event.getPath());
                    break;
                case NodeDataChanged:
                    log.info("Data changed for node {}", event.getPath());
                    nodeDataChanged(event.getPath());
                    break;
            }
        }
    }

    public ZVModelImpl(String connectString) throws IOException {
        this.watcher = new ZkWatcher();
        this.zk = new ZooKeeper(connectString, 3000, this.watcher);
        // s this.watcherExecutor.execute(this.watcher);

        log.info("[{}] AFTER ZK INIT", Thread.currentThread());
        synchronized (watcher.lock) {
            while (watcher.dead) {
                try {
                    log.info("[{}] Awaiting lock notification", Thread.currentThread());
                    watcher.lock.wait();
                    log.info("[{}] Lock notification, watcher.dead = {}",Thread.currentThread() ,watcher.dead);
                } catch (InterruptedException e) {
                    log.error("=====> Interrupted while waiting for watcher lock.", e);
                }
            }
        }
        populateRoot();

    }

    /*
     * (non-Javadoc)
     * 
     * @see net.isammoc.zooviewer.model.ZVModel#close()
     */
    @Override
    public void close() throws InterruptedException {
        log.info("Closing ZooKeeper client...");
        zk.close();
        synchronized (watcher.lock) {
            watcher.dead = true;
            watcher.lock.notifyAll();
        }
        log.info("Shutting down watcher...");
        watcherExecutor.shutdown();
        log.info("Removing listeners...");
        ZVModelListener[] listeners = listenerList.getListeners(ZVModelListener.class);
        for (ZVModelListener listener : listeners) {
            listenerList.remove(ZVModelListener.class, listener);
        }

        log.info("Resetting models...");
        nodes.clear();
        children.clear();
        log.info("Close done.");
    }

    /**
     * Called when a node has been deleted in the ZooKeeper model.
     * @param path the node path
     */
    private synchronized void nodeDeleted(String path) {
        ZVNodeImpl oldNode = nodes.get(path);
        if (oldNode != null) {
            oldNode.setExists(false);
            oldNode.setStat(null);
            ZVNodeImpl parent = nodes.get(getParent(path));
            int oldIndex = children.get(parent).indexOf(oldNode);
            children.get(parent).remove(oldNode);
            fireNodeDeleted(oldNode, oldIndex);
        }
    }

    /**
     * Called when a node has been updated in the ZooKeeper model.
     * @param path the node path
     */
    private synchronized void nodeDataChanged(String path) {
        ZVNodeImpl node = nodes.get(path);
        try {
            Stat stat = new Stat();
            node.setData(zk.getData(path, watcher, stat));
            node.setStat(stat);
            fireNodeDataChanged(node);
        } catch (KeeperException | InterruptedException e) {
            log.error("Error getting new node data.", e);
        }
    }

    /**
     * Populates the root in this model.
     */
    private synchronized void populateRoot() {
        if (nodes.get("/") == null) {
            try {
                log.info("[{}] Populating root..", Thread.currentThread());
                Stat stat = new Stat();
                ZVNodeImpl root = new ZVNodeImpl("/", zk.getData("/", watcher, stat));
                root.setStat(stat);
                nodes.put("/", root);
                children.put(root, new ArrayList<>());
                fireNodeCreated(root);
                populateChildren("/");
            } catch (KeeperException | InterruptedException e) {
                log.error("Error populating root.", e);
            }
        }
    }

    /**
     * Populates the children of the specified path.
     * @param path path
     */
    private synchronized void populateChildren(String path) {
        ChildrenCallback cb = (rc, path1, ctx, childrenNames) -> {
            ZVNodeImpl parent = nodes.get(path1);
            Stat stat = new Stat();
            try {
                parent.setStat(zk.exists(path1, false));
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
            for (String childName : childrenNames) {
                try {
                    String childPath = getFullPath(path1, childName);
                    ZVNodeImpl child = nodes.get(childPath);
                    if (child != null) {
                        if (!child.exists()) {
                            child.setData(zk.getData(childPath, watcher,
                                    stat));
                            child.setStat(stat);
                            child.setExists(true);
                            children.put(child, new ArrayList<>());
                            children.get(parent).add(child);
                            fireNodeCreated(child);
                            populateChildren(childPath);
                        }
                    } else {
                        child = new ZVNodeImpl(childPath, zk.getData(
                                childPath, watcher, stat));
                        child.setStat(stat);
                        nodes.put(childPath, child);
                        children.put(child, new ArrayList<>());
                        children.get(parent).add(child);
                        fireNodeCreated(child);
                        populateChildren(childPath);
                    }
                } catch (Exception ignore) {
                    ignore.printStackTrace();
                }
            }
        };
        zk.getChildren(path, watcher, cb, null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.isammoc.zooviewer.model.ZVModel#getFullPath(java.lang.String,
     * java.lang.String)
     */
    @Override
    public String getFullPath(String parentPath, String childName) {
        return ("/".equals(parentPath) ? "/" : (parentPath + "/")) + childName;
    }

    private String getParent(String path) {
        if ("/".equals(path)) {
            return null;
        } else {
            int lastIndex = path.lastIndexOf("/");
            if (lastIndex > 0) {
                return path.substring(0, lastIndex);
            } else {
                return "/";
            }
        }
    }

    @Override
    public void addNode(String path, byte[] data) {
        if ((nodes.get(path) != null) && nodes.get(path).exists()) {
            throw new IllegalStateException("Node '" + path + "' already exists");
        }

        if ((nodes.get(getParent(path)) == null) || !nodes.get(getParent(path)).exists()) {
            throw new IllegalArgumentException("Node '" + path + "' can't be created. Its parent node doesn't exist");
        }

        try {
            zk.create(path, data, OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException | InterruptedException e) {
            log.error("Error adding node.", e);
        }
    }

    @Override
    public void deleteNode(ZVNode node) {
        String path = node.getPath();
        log.info("Delete requested on node " + path);
        PathUtils.validatePath(path);
        try {
            // Checks if the node has children
            List<String> childNodes = zk.getChildren(path, false);
            if (childNodes != null && childNodes.size() > 0) {
                // if the node has children, delete them recursively
                for (String nodeName : childNodes) {
                    String childPath = path + (path.endsWith("/") ? "" : "/") + nodeName;
                    deleteNode(getNode(childPath));
                }
            }
            // finally, delete the node itself
            Stat stat = zk.exists(path, false);
            log.info("Deleting node {} (stat = {})", path, stat);
            zk.delete(path, -1);
            Stat stat2 = zk.exists(path, false);
            log.info("Deleting node {} (stat = {})", path, stat2);
        } catch (KeeperException | InterruptedException e) {
            log.error("Error deleting node.", e);
        }
    }

    @Override
    public void deleteNodes(ZVNode[] nodes) {
        for (ZVNode node : nodes) {
            deleteNode(node);
        }

    }

    @Override
    public void updateData(String path, byte[] data) {
        try {
            Stat stat = zk.setData(path, data, -1);
            nodes.get(path).setStat(stat);
        } catch (KeeperException | InterruptedException e) {
            log.error("Error updating data.", e);
        }
    }

    @Override
    public ZVNode getNode(String path) {
        return nodes.get(path);
    }

    @Override
    public ZVNode getParent(ZVNode node) {
        return getNode(getParent(node.getPath()));
    }

    @Override
    public List<ZVNode> getChildren(ZVNode parent) {
        return children.get(parent).stream().filter(ZVNodeImpl::exists).collect(Collectors.toList());
    }

    @Override
    public void addModelListener(ZVModelListener listener) {
        listenerList.add(ZVModelListener.class, listener);
    }

    @Override
    public void removeModelListener(ZVModelListener listener) {
        listenerList.remove(ZVModelListener.class, listener);
    }

    protected void fireNodeCreated(ZVNode newNode) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ZVModelListener.class) {
                ((ZVModelListener) listeners[i + 1]).nodeCreated(newNode);
            }
        }
    }

    protected void fireNodeDeleted(ZVNode oldNode, int oldIndex) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ZVModelListener.class) {
                ((ZVModelListener) listeners[i + 1]).nodeDeleted(oldNode, oldIndex);
            }
        }
    }

    protected void fireNodeDataChanged(ZVNode node) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ZVModelListener.class) {
                ((ZVModelListener) listeners[i + 1]).nodeDataChanged(node);
            }
        }
    }
}
