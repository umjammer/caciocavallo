package net.java.openjdk.awt.peer.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;


public class WebEventManager {

    final WebMouseStateTracker mouseTracker;
    final WebKeyboardStateTracker keyboardTracker;
    final WebSessionState state;

    final HashMap<Integer, String[]> eventIDMap = new HashMap<>();
    int highestDispatchedEventID = 0;

    public WebEventManager(WebSessionState state) {
        this.state = state;

        mouseTracker = new WebMouseStateTracker(state.getScreen());
        keyboardTracker = new WebKeyboardStateTracker(state.getScreen());
    }

    public void parseEventData(int eventID, String paramStr) {
        state.lockSession();
        try {
            String[] params = paramStr.split("_");

            if (eventID == -1 || (eventID == highestDispatchedEventID + 1 && eventIDMap.isEmpty())) {
                dispatchEvent(params);
                highestDispatchedEventID = eventID;
            } else {
                eventIDMap.put(eventID, params);

                ArrayList<Integer> sortedEventIDList = new ArrayList<>(eventIDMap.keySet());
                Collections.sort(sortedEventIDList);
                int minID = sortedEventIDList.getFirst();
                int maxId = sortedEventIDList.getLast();

                if (minID == highestDispatchedEventID + 1 && (maxId - minID) == (sortedEventIDList.size() - 1)) {
                    for (int curEvId : sortedEventIDList) {
                        dispatchEvent(eventIDMap.get(curEvId));
                    }

                    highestDispatchedEventID = maxId;
                    eventIDMap.clear();
                }
            }
        } finally {
            state.unlockSession();
        }
    }

    protected void dispatchEvent(String[] params) {
        LinkedList<String> eventDataList = new LinkedList<>(Arrays.asList(params));

        while (!eventDataList.isEmpty()) {
            String command = eventDataList.removeFirst();
            if (!command.isEmpty()) {

                if (command.equals("M")) {
                    processMouseEvent(eventDataList);
                } else if (command.equals("MM")) {
                    processMouseMotionEvent(eventDataList);
                } else if (command.equals("MW")) {
                    processMouseWheelEvent(eventDataList);
                } else if (command.equals("K")) {
                    processKeyEvent(eventDataList);
                } else if (command.equals("S")) {
                    processResizeEvent(eventDataList);
                }
            }
        }
    }

    protected void processResizeEvent(LinkedList<String> params) {
        int w = Integer.parseInt(params.removeFirst());
        int h = Integer.parseInt(params.removeFirst());

        state.getGraphicsConfiguration().getScreen().resizeScreen(w, h);
    }

    protected void processKeyEvent(LinkedList<String> params) {
        boolean down = Integer.parseInt(params.removeFirst()) > 0;
        int keySym = Integer.parseInt(params.removeFirst());
        char charVal = 0;
        boolean ctrl = Boolean.parseBoolean(params.removeFirst());
        boolean shift = Boolean.parseBoolean(params.removeFirst());
        boolean alt = Boolean.parseBoolean(params.removeFirst());
        keyboardTracker.trackKeyEvent(down, keySym, charVal, ctrl, shift, alt);
    }

    protected void processMouseMotionEvent(LinkedList<String> params) {
        int x = Integer.parseInt(params.removeFirst());
        int y = Integer.parseInt(params.removeFirst());

        mouseTracker.trackMouseMotionEvent(x, y);
    }

    protected void processMouseWheelEvent(LinkedList<String> params) {
        boolean up = Integer.parseInt(params.removeFirst()) > 0;
        int x = Integer.parseInt(params.removeFirst());
        int y = Integer.parseInt(params.removeFirst());

        mouseTracker.trackMouseWheelEvent(up, x, y);
    }

    protected void processMouseEvent(LinkedList<String> params) {
        int x = Integer.parseInt(params.removeFirst());
        int y = Integer.parseInt(params.removeFirst());
        boolean down = Integer.parseInt(params.removeFirst()) > 0;
        int buttonMask = Integer.parseInt(params.removeFirst());

        mouseTracker.trackMouseEvent(down, buttonMask, x, y);
    }
}
