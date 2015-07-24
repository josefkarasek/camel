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

import static org.apache.camel.test.junit4.TestSupport.deleteDirectory;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.ContextInject;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.apache.camel.test.junit4.annotation.MockEndpoints;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CamelRunner.class)
@MockEndpoints("file:target*")
public class RunnerIsMockEndpointsFileTest {
    
    @ContextInject
    public CamelContext context;
    
    @EndpointInject(uri = "mock:file:target/messages/camel")
    public MockEndpoint camelEndpoint;
    
    @EndpointInject(uri = "mock:file:target/messages/others")
    public MockEndpoint othersEndpoint;
    
    @Produce(uri = "direct:start")
    public ProducerTemplate template;

    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/input");
        deleteDirectory("target/messages");
    }

    @Test
    public void testMockFileEndpoints() throws Exception {
        camelEndpoint.expectedMessageCount(1);
        othersEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/input", "Hello Camel", Exchange.FILE_NAME, "camel.txt");
        template.sendBodyAndHeader("file:target/input", "Hello World", Exchange.FILE_NAME, "world.txt");

        camelEndpoint.assertIsSatisfied();
        othersEndpoint.assertIsSatisfied();
    }

    @CreateRouteBuilder
    public RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/input")
                    .choice()
                        .when(body(String.class).contains("Camel")).to("file:target/messages/camel")
                        .otherwise().to("file:target/messages/others");
            }
        };
    }
}