/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.tools.dap.types.SetVariableArguments;
import com.oracle.truffle.tools.dap.types.Variable;
import com.oracle.truffle.tools.dap.types.VariablesArguments;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class VariablesHandler {

    private final ExecutionContext context;

    public VariablesHandler(ExecutionContext context) {
        this.context = context;
    }

    public List<Variable> getVariables(ThreadsHandler.SuspendedThreadInfo info, VariablesArguments args) {
        List<Variable> vars = new ArrayList<>();
        DebugScope dScope;
        int id = args.getVariablesReference();
        StackFramesHandler.ScopeWrapper scopeWrapper = info.getById(StackFramesHandler.ScopeWrapper.class, id);
        if (scopeWrapper != null) {
            dScope = scopeWrapper.getScope();
            if (scopeWrapper.getReturnValue() != null) {
                vars.add(createVariable(info, scopeWrapper.getReturnValue(), "Return value"));
            }
            if (scopeWrapper.getThisValue() != null) {
                vars.add(createVariable(info, scopeWrapper.getThisValue(), "Receiver"));
            }
        } else {
            dScope = info.getById(DebugScope.class, id);
        }
        if (dScope != null) {
            for (DebugValue val : dScope.getDeclaredValues()) {
                if (context.isInspectInternal() || !val.isInternal()) {
                    vars.add(createVariable(info, val, "Unnamed value"));
                }
            }
        } else {
            DebugValue dValue = info.getById(DebugValue.class, id);
            if (dValue != null) {
                if (dValue.isArray()) {
                    for (DebugValue val : dValue.getArray()) {
                        if (context.isInspectInternal() || !val.isInternal()) {
                            vars.add(createVariable(info, val, "Unnamed value"));
                        }
                    }
                }
                Collection<DebugValue> properties = dValue.getProperties();
                if (properties != null) {
                    for (DebugValue val : properties) {
                        if (context.isInspectInternal() || !val.isInternal()) {
                            vars.add(createVariable(info, val, "Unnamed value"));
                        }
                    }
                }
            }
        }
        return vars;
    }

    public static Variable setVariable(ThreadsHandler.SuspendedThreadInfo info, SetVariableArguments args) {
        DebugValue value = null;
        DebugValue newValue = null;
        int id = args.getVariablesReference();
        String name = args.getName();
        StackFramesHandler.ScopeWrapper scopeWrapper = info.getById(StackFramesHandler.ScopeWrapper.class, id);
        if (scopeWrapper != null) {
            newValue = scopeWrapper.getFrame().eval(args.getValue());
            value = scopeWrapper.getScope().getDeclaredValue(name);
        } else {
            newValue = info.getSuspendedEvent().getTopStackFrame().eval(args.getValue());
            DebugScope dScope = info.getById(DebugScope.class, id);
            if (dScope != null) {
                value = dScope.getDeclaredValue(name);
            } else {
                DebugValue dValue = info.getById(DebugValue.class, id);
                if (dValue != null) {
                    value = dValue.getProperty(name);
                    if (value == null && dValue.isArray()) {
                        try {
                            value = dValue.getArray().get(Integer.parseInt(name));
                        } catch (NumberFormatException ex) {
                        }
                    }
                }
            }
        }
        if (value != null && value.isWritable() && newValue != null && newValue.isReadable()) {
            value.set(newValue);
            return createVariable(info, newValue, "");
        }
        return null;
    }

    static Variable createVariable(ThreadsHandler.SuspendedThreadInfo info, DebugValue val, String defaultName) throws DebugException {
        Collection<DebugValue> properties = val.getProperties();
        int valId = (val.isArray() && !val.getArray().isEmpty()) || (properties != null && !properties.isEmpty()) ? info.getId(val) : 0;
        Variable var = Variable.create(val.getName() != null ? val.getName() : defaultName,
                        val.isReadable() ? val.isString() ? '"' + val.toDisplayString() + '"' : val.toDisplayString() : "",
                        valId);
        DebugValue metaObject = val.getMetaObject();
        if (metaObject != null) {
            var.setType(metaObject.getMetaSimpleName());
        }
        if (val.isArray()) {
            var.setIndexedVariables(val.getArray().size());
        }
        if (properties != null) {
            var.setNamedVariables(properties.size());
        }
        return var;
    }
}
