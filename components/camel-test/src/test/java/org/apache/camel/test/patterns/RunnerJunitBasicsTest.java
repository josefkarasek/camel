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
package org.apache.camel.test.patterns;

import static org.junit.Assert.assertEquals;

import org.apache.camel.test.junit4.CamelRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CamelRunner.class)
public class RunnerJunitBasicsTest {

    private static String testString = "Not initialized";
    
    @BeforeClass
    public static void beforeClassInit() {
        assertEquals("Not initialized", testString);
        testString = "Before class init";
    }
    
    @Before
    public void before() {
        assertEquals("Before class init", testString);
        testString = "Before init";
    }
    
    @Test
    public void testSimpleChange() {
        assertEquals("Before init", testString);
        testString = "Test init";
    }
    
    @After
    public void after() {
        assertEquals("Test init", testString);
        testString = "After init";
    }
    
    @AfterClass
    public static void afterClass() {
        assertEquals("After init", testString);
        testString = null;
    }
}
