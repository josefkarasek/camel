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

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.ContextInject;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.apache.camel.test.junit4.annotation.MockEndpointsAndSkip;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @version 
 */
@MockEndpointsAndSkip("direct:foo")
@RunWith(CamelRunner.class)
public class RunnerIsMockEndpointsAndSkipJUnit4Test {
    
    @ContextInject
    public CamelContext context;
    
    @EndpointInject(uri = "mock:result")
    public MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    public ProducerTemplate template;

    @Test
    public void testMockEndpointAndSkip() throws Exception {
        // notice we have automatic mocked the direct:foo endpoints and the name of the endpoints is "mock:uri"
        resultEndpoint.expectedBodiesReceived("Hello World");
        resultEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        resultEndpoint.assertIsSatisfied();

        // the message was not send to the direct:foo route and thus not sent to the seda endpoint
        SedaEndpoint seda = context.getEndpoint("seda:foo", SedaEndpoint.class);
        assertEquals(0, seda.getCurrentQueueSize());
    }

    @CreateRouteBuilder
    public RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("direct:foo").to("mock:result");

                from("direct:foo").transform(constant("Bye World")).to("seda:foo");
            }
        };
    }
}
