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

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.ContextInject;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.apache.camel.test.junit4.annotation.MockEndpoints;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CamelRunner.class)
@MockEndpoints  // the default value is "*"
public class RunnerIsMockEndpointsJUnit4Test {
    
    @ContextInject
    public CamelContext context;
    
    @EndpointInject(uri = "mock:direct:start")
    public MockEndpoint startEndpoint;
    
    @EndpointInject(uri = "mock:direct:foo")
    public MockEndpoint directFooEndpoint;
    
    @EndpointInject(uri = "mock:log:foo")
    public MockEndpoint logFooEndpoint;
    
    @EndpointInject(uri = "mock:result")
    public MockEndpoint resultEndpoint;
    
    @Produce(uri = "direct:start")
    public ProducerTemplate template;

    @Test
    public void testMockAllEndpoints() throws Exception {
        startEndpoint.expectedBodiesReceived("Hello World");
        directFooEndpoint.expectedBodiesReceived("Hello World");
        logFooEndpoint.expectedBodiesReceived("Bye World");
        resultEndpoint.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        startEndpoint.assertIsSatisfied();
        directFooEndpoint.assertIsSatisfied();
        logFooEndpoint.assertIsSatisfied();
        resultEndpoint.assertIsSatisfied();

        // additional test to ensure correct endpoints in registry
        assertNotNull(context.hasEndpoint("direct:start"));
        assertNotNull(context.hasEndpoint("direct:foo"));
        assertNotNull(context.hasEndpoint("log:foo"));
        assertNotNull(context.hasEndpoint("mock:result"));
        // all the endpoints was mocked
        assertNotNull(context.hasEndpoint("mock:direct:start"));
        assertNotNull(context.hasEndpoint("mock:direct:foo"));
        assertNotNull(context.hasEndpoint("mock:log:foo"));
    }

    @CreateRouteBuilder
    public RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("direct:foo").to("log:foo").to("mock:result");

                from("direct:foo").transform(constant("Bye World"));
            }
        };
    }
}
