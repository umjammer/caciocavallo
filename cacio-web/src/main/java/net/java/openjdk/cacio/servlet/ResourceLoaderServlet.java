/*
 * Copyright (c) 2011, Clemens Eisserer, Oracle and/or its affiliates. All rights reserved.
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

package net.java.openjdk.cacio.servlet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Servlet for loading Resources located on the classpath.
 * Useful when deploying resources which are packed in jar-files.
 *
 * @author Clemens Eisserer <linuxhippy@gmail.com>
 */
public class ResourceLoaderServlet extends HttpServlet {

    String startHtml = null;
    private static final Pattern SAFE_SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9.-]+");

    public ResourceLoaderServlet() throws Exception {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String resourceName = toSafeResourcePath(request.getParameter("res"));
        if (resourceName == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid resource name");
            return;
        }

        InputStream raw = getClass().getResourceAsStream(resourceName);
        if (raw == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        OutputStream os = response.getOutputStream();
        InputStream is = new BufferedInputStream(raw);
        int read;
        while ((read = is.read()) != -1) {
            os.write(read);
        }
    }

    private String toSafeResourcePath(String rawResourceName) {
        if (rawResourceName == null || rawResourceName.trim().isEmpty()) {
            return null;
        }

        String normalized = rawResourceName.trim().replace('_', '/');
        if (normalized.startsWith("/") || normalized.contains("\\") || normalized.contains("..")) {
            return null;
        }

        String[] parts = normalized.split("/");
        List<String> safeParts = new ArrayList<String>();
        for (String part : parts) {
            if (part == null || part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return null;
            }
            if (!SAFE_SEGMENT_PATTERN.matcher(part).matches()) {
                return null;
            }
            safeParts.add(part);
        }

        if (safeParts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("/");
        for (int i = 0; i < safeParts.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(safeParts.get(i));
        }
        return sb.toString();
    }
}

