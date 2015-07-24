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

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.ContextInject;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.apache.camel.test.junit4.annotation.UseAdviceWith;
import org.apache.camel.test.junit4.annotation.UseOverridePropertiesWithPropertiesComponent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Properties;

@RunWith(CamelRunner.class)
@UseAdviceWith
public class RunnerUseOverridePropertiesWithPropertiesComponentTest {
    
    @ContextInject
    public CamelContext context;
    
    @EndpointInject(uri = "mock:file")
    public MockEndpoint resultEndpoint;

    @Produce(uri = "direct:sftp")
    public ProducerTemplate template;

    @Before
    @SuppressWarnings("deprecation")
    public void doSomethingBefore() throws Exception {
        AdviceWithRouteBuilder mocker = new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:sftp");

                interceptSendToEndpoint("file:*")
                        .skipSendToOriginalEndpoint()
                        .to("mock:file");
            }
        };
        context.getRouteDefinition("myRoute").adviceWith(context, mocker);
    }

    @UseOverridePropertiesWithPropertiesComponent
    public Properties useOverridePropertiesWithPropertiesComponent() {
        Properties pc = new Properties();
        pc.put("ftp.username", "scott");
        pc.put("ftp.password", "tiger");
        return pc;
    }

    @Test
    public void testOverride() throws Exception {
        context.start();

        resultEndpoint.expectedMessageCount(1);

        template.sendBody("direct:sftp", "Hello World");

        resultEndpoint.assertIsSatisfied();
    }

    @CreateRouteBuilder
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ftp:somepath?username={{ftp.username}}&password={{ftp.password}}").routeId("myRoute")
                    .to("file:target/out");
            }
        };
    }
}
