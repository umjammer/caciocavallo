/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package net.java.openjdk.cacio.ctc;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JLabel;

import net.java.openjdk.cacio.ctc.junit.CacioFESTRunner;

import org.fest.swing.annotation.GUITest;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.Containers;
import org.fest.swing.fixture.FrameFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CacioFESTRunner.class)
public class ClickEventTestFESTTest {

    private FrameFixture ff;
    private List<MouseEvent> events;

    @BeforeEach
    public void setUp() throws Exception {
        events = new LinkedList<>();
        final JLabel label = new JLabel("label");
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                events.add(e);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                events.add(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                events.add(e);
            }
        });
        label.setName("label");
        ff = Containers.showInFrame(label);

    }

    @AfterEach
    public void tearDown() throws Exception {
        ff.cleanUp();
        ff = null;
        events = null;
    }

    @Test
    @GUITest
    public void singleClickEvent() {
        JLabelFixture lf = ff.label("label");
//        lf.robot.moveMouse(lf.target);
        lf.robot.moveMouse(lf.target, new Point(10, 10));
        lf.robot.pressMouse(MouseButton.LEFT_BUTTON);
        lf.robot.releaseMouse(MouseButton.LEFT_BUTTON);
        Point screenLoc = lf.target.getLocationOnScreen();
        screenLoc.translate(10, 10);
        checkClick(MouseEvent.BUTTON1, screenLoc, new Point(10, 10), false);
    }

    @Test
    @GUITest
    public void doubleClickEvent() {
        JLabelFixture lf = ff.label("label");
        lf.robot.moveMouse(lf.target);

        lf.robot.pressMouse(MouseButton.LEFT_BUTTON);
        lf.robot.releaseMouse(MouseButton.LEFT_BUTTON);
        lf.robot.pressMouse(MouseButton.LEFT_BUTTON);
        lf.robot.releaseMouse(MouseButton.LEFT_BUTTON);
        checkDoubleClick(MouseEvent.BUTTON1);
    }

    @Test
    @GUITest
    public void verifyPressReleaseDifferentLocations() {
        JLabelFixture lf = ff.label("label");
        lf.robot.moveMouse(lf.target, new Point(10, 10));
        lf.robot.pressMouse(MouseButton.LEFT_BUTTON);
        lf.robot.moveMouse(lf.target, new Point(10, 11));
        lf.robot.releaseMouse(MouseButton.LEFT_BUTTON);
        Assertions.assertEquals(2, events.size()); // Missing click event.
    }

    @Test
    @GUITest
    public void verifyClickMiddleMouse() {
        JLabelFixture lf = ff.label("label");
        lf.click(MouseButton.MIDDLE_BUTTON);
        checkClick(MouseEvent.BUTTON2, null, null, false);
    }

    @Test
    @GUITest
    public void verifyClickRightMouse() {
        JLabelFixture lf = ff.label("label");
        lf.click(MouseButton.RIGHT_BUTTON);
        checkClick(MouseEvent.BUTTON3, null, null, true);
    }

    private void checkClick(int button, Point screenLoc, Point loc, boolean popup) {

        Assertions.assertEquals(3, events.size());
        Assertions.assertEquals(MouseEvent.MOUSE_PRESSED, events.get(0).getID());
        Assertions.assertEquals(MouseEvent.MOUSE_RELEASED, events.get(1).getID());
        Assertions.assertEquals(MouseEvent.MOUSE_CLICKED, events.get(2).getID());
        Assertions.assertFalse(events.get(0).isPopupTrigger());
        Assertions.assertEquals(popup, events.get(1).isPopupTrigger());
        Assertions.assertFalse(events.get(2).isPopupTrigger());
        for (MouseEvent event : events) {
            Assertions.assertEquals(1, event.getClickCount());
            Assertions.assertEquals(button, event.getButton());
            if (screenLoc != null) {
                Assertions.assertEquals(screenLoc, new Point(event.getXOnScreen(), event.getYOnScreen()));
            }
            if (loc != null) {
                Assertions.assertEquals(loc, new Point(event.getX(), event.getY()));
            }
        }
    }

    private void checkDoubleClick(int button) {

        Assertions.assertEquals(6, events.size());
        Assertions.assertEquals(MouseEvent.MOUSE_PRESSED, events.get(0).getID());
        Assertions.assertEquals(1, events.get(0).getClickCount());
        Assertions.assertEquals(button, events.get(0).getButton());
        Assertions.assertEquals(MouseEvent.MOUSE_RELEASED, events.get(1).getID());
        Assertions.assertEquals(1, events.get(1).getClickCount());
        Assertions.assertEquals(button, events.get(1).getButton());
        Assertions.assertEquals(MouseEvent.MOUSE_CLICKED, events.get(2).getID());
        Assertions.assertEquals(1, events.get(2).getClickCount());
        Assertions.assertEquals(button, events.get(2).getButton());
        Assertions.assertEquals(MouseEvent.MOUSE_PRESSED, events.get(3).getID());
        Assertions.assertEquals(2, events.get(3).getClickCount());
        Assertions.assertEquals(button, events.get(3).getButton());
        Assertions.assertEquals(MouseEvent.MOUSE_RELEASED, events.get(4).getID());
        Assertions.assertEquals(2, events.get(4).getClickCount());
        Assertions.assertEquals(button, events.get(4).getButton());
        Assertions.assertEquals(MouseEvent.MOUSE_CLICKED, events.get(5).getID());
        Assertions.assertEquals(2, events.get(5).getClickCount());
        Assertions.assertEquals(button, events.get(5).getButton());
    }

}
