/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.test.junit4;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Performs tear down operations of Camel after last {@code @After} method returned.
 *  
 * @author <a href="mailto:karasek.jose@gmail.com">Josef Karásek</a>
 *
 */
public class RunTeardown extends Statement {

    private final FrameworkMethod method;
    private final CamelRunner runner;
    private final Statement base;

    RunTeardown(FrameworkMethod method, CamelRunner runner, Statement base) {
        this.method = method;
        this.runner = runner;
        this.base = base;
    }
    
    @Override
    public void evaluate() throws Throwable {
        try {
            base.evaluate();
        } finally {
            runner.tearDown(method);
        }
    }
}
