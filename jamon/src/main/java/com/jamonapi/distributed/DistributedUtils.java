package com.jamonapi.distributed;

import com.jamonapi.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Get a list of listeners associated with a monitor.
 */
class DistributedUtils {
    private static final String FIFO_BUFFER = "FIFOBuffer";
    static final int DEFAULT_BUFFER_SIZE = 250;

    public static List<ListenerInfo> getAllListeners(Monitor monitor) {
        List<ListenerInfo> list = new ArrayList<ListenerInfo>();
        // min, max, value, maxactive
        addAllListeners(list, monitor, "value");
        addAllListeners(list, monitor, "min");
        addAllListeners(list, monitor, "max");
        addAllListeners(list, monitor, "maxactive");
        return list;
    }

    /**
     * Copy
     *
     * @param from
     * @param to
     */
    public static void copyJamonBufferListenerData(Monitor from, Monitor to) {
        List<DistributedUtils.ListenerInfo> listeners = DistributedUtils.getAllListeners(from);
        Iterator<DistributedUtils.ListenerInfo> iter = listeners.iterator();
        while (iter.hasNext()) {
            DistributedUtils.ListenerInfo listenerInfo = iter.next();
            // only copying jamon buffer listener data - for now. ignoring other listeners
            if (listenerInfo.getListener() instanceof JAMonBufferListener) {
                String fifoName = getFifoBufferName(listenerInfo.getListener().getName());
                JAMonBufferListener fromJamonBufferListener = (JAMonBufferListener) listenerInfo.getListener();
                JAMonBufferListener toJamonBufferListener = null;
                if (to.hasListener(listenerInfo.getListenerType(), fifoName)) {
                    toJamonBufferListener = (JAMonBufferListener) to.getListenerType(listenerInfo.getListenerType()).getListener(fifoName);
                } else {
                    toJamonBufferListener = createBufferListener(fifoName, fromJamonBufferListener);
                    toJamonBufferListener.getBufferList().reset();
                    to.addListener(listenerInfo.getListenerType(), toJamonBufferListener);
                }

                copyBufferListenerData(fromJamonBufferListener, toJamonBufferListener, from.getMonKey().getInstanceName());
            }
        }
    }

    /**
     * Copy buffer data from the from/source listener to the to/destination buffer listener.
     *
     * @param from source data
     * @param to   destination data
     */
    private static void copyBufferListenerData(JAMonBufferListener from, JAMonBufferListener to, String instanceName) {
        if (from.hasData()) {
            final int LABEL = 0;
            Object[][] data = from.getBufferList().getDetailData().getData();
            for (Object[] row : data) {
                // add instance name to monitor label - example: select * from table where name='steve' - (tomcat8_production)
                // if the key setDetails was called label will be that value or else it will be the monitor label
                if (row != null && row[LABEL] != null) {
                    row[LABEL] = new StringBuilder().append(row[LABEL]).append(" - (instanceName: ").append(instanceName).append(")").toString();
                }
                // note addRow will honor the rules of the given buffer listener. For example a fifo buffer listener will always
                // add the row, whereas a max listener will only add it if it is a new max.
                to.addRow(row);
            }
        }

    }

    private static void addAllListeners(List<ListenerInfo> list, Monitor monitor, String listenerTypeName) {
        ListenerType listenerType = monitor.getListenerType(listenerTypeName);
        if (listenerType == null) {
            return;
        }

        JAMonListener listener = listenerType.getListener();
        if (listener == null) {
            return;
        }

        addAllListeners(list, listenerTypeName, listener);

    }

    private static void addAllListeners(List<ListenerInfo> list, String listenerTypeName, JAMonListener listener) {
        if (listener instanceof CompositeListener) {
            CompositeListener compositeListener = (CompositeListener) listener;
            Iterator iterator = compositeListener.iterator();
            while (iterator.hasNext()) {
                addAllListeners(list, listenerTypeName, (JAMonListener) iterator.next());
            }
        } else if (listener != null) {
            list.add(new ListenerInfo(listenerTypeName, listener));
        }

    }

    private static JAMonBufferListener createBufferListener(String name, JAMonBufferListener from) {
        JAMonBufferListener fifo = (JAMonBufferListener) from.copy();
        fifo.getBufferList().setBufferSize(DEFAULT_BUFFER_SIZE);
        fifo.setName(name);
        return fifo;
    }

    static String getFifoBufferName(String name) {
        return name + "_aggregated";
    }

    public static class ListenerInfo {
        public ListenerInfo(String listenerType, JAMonListener listener) {
            this.listenerType = listenerType;
            this.listener = listener;
        }

        private String listenerType;
        private JAMonListener listener;

        public String getListenerType() {
            return listenerType;
        }

        public JAMonListener getListener() {
            return listener;
        }

    }
}
