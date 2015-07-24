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

import static org.junit.Assert.assertNotNull;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.CreateCamelContextPerClass;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests filtering using Camel Test
 *
 * @version 
 */
@CreateCamelContextPerClass
@RunWith(CamelRunner.class)
public class RunnerFilterCreateCamelContextPerClassTest {
    
    @EndpointInject(uri = "mock:result")
    public MockEndpoint mock;

    @Produce(uri = "direct:start")
    public ProducerTemplate template;


    @Test
    public void testSendMatchingMessage() throws Exception {
        assertNotNull("No mock object found!", mock);
        
        String expectedBody = "<matched/>";

        mock.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader("direct:start", expectedBody, "foo", "bar");

        mock.assertIsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        assertNotNull("No template found!", template);
        assertNotNull("No mock found!", mock);
        
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "<notMatched/>", "foo", "notMatchedHeaderValue");

        mock.assertIsSatisfied();
    }

    @CreateRouteBuilder
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").filter(header("foo").isEqualTo("bar")).to("mock:result");
            }
        };
    }
}