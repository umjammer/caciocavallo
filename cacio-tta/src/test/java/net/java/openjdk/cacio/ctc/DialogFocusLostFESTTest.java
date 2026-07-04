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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextField;

import net.java.openjdk.cacio.ctc.junit.CacioFESTRunner;
import org.fest.swing.annotation.GUITest;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.FrameFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CacioFESTRunner.class)
public class DialogFocusLostFESTTest {

    private JFrame frame;
    private FrameFixture ff;

    @BeforeEach
    public void setUp() throws Exception {
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() throws Throwable {
                frame = new JFrame();
                frame.setLayout(new FlowLayout());
                final JButton b = new JButton("button");
                b.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final JDialog dialog = new JDialog(frame, "Test", true);
                        JTextField textField = new JTextField();
                        dialog.getContentPane().add(textField);
                        JButton okButton = new JButton(new AbstractAction("Ok") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                dialog.setVisible(false);
                                dialog.dispose();
                            }
                        });
                        okButton.setName("Ok");
                        dialog.getContentPane().add(okButton);
                        dialog.pack();
                        dialog.setVisible(true);
                    }
                });
                b.setName("button");
                frame.add(b);
                JTextField text = new JTextField("foo");
                text.setName("text");
                frame.add(text);
                frame.pack();
                frame.setVisible(true);
            }
        });
        ff = new FrameFixture(frame);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ff.cleanUp();
        GuiActionRunner.execute(new GuiTask() {

            @Override
            protected void executeInEDT() throws Throwable {
                frame.dispose();
                frame = null;
            }
        });
    }

    @Test
    @GUITest
    public void testDialogFocusAfterClose() {
        JButtonFixture b = ff.button("button");
        b.click();

        DialogFixture df = WindowFinder.findDialog(JDialog.class).using(ff.robot);
        df.button("Ok").click();

        JTextComponentFixture t = ff.textBox("text");
        t.focus();
        t.requireFocused();
    }
}
