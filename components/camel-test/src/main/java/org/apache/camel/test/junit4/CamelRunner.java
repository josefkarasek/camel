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

import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.impl.InterceptSendToMockEndpointStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.test.junit4.annotation.AnnotationScanner;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.apache.camel.test.junit4.annotation.ProvidesBreakpoint;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:karasek.jose@gmail.com">Josef Karásek</a>
 *
 */
public class CamelRunner extends BlockJUnit4ClassRunner {
    
    private final Class<?> testClass;
    protected Logger log = LoggerFactory.getLogger(getClass());
    private static final ThreadLocal<Boolean> INIT = new ThreadLocal<Boolean>();
    private static ThreadLocal<ModelCamelContext> threadCamelContext = new ThreadLocal<ModelCamelContext>();
    private static ThreadLocal<ProducerTemplate> threadTemplate = new ThreadLocal<ProducerTemplate>();
    private static ThreadLocal<ConsumerTemplate> threadConsumer = new ThreadLocal<ConsumerTemplate>();
    private static ThreadLocal<Service> threadService = new ThreadLocal<Service>();
    protected volatile ModelCamelContext context;
    protected volatile ProducerTemplate template;
    protected volatile ConsumerTemplate consumer;
    protected volatile Service camelContextService;
    private final Map<String, String> fromEndpoints = new HashMap<String, String>();
    private final StopWatch watch = new StopWatch();
    private AnnotationScanner scanner;
    private Object testClassInstance;

    
    /**
     * Constructs object of the CamelRunner.
     * 
     * @param klass the test class that is being tested. 
     * @throws InitializationError
     */
    public CamelRunner(Class<?> klass) throws InitializationError {
        super(klass);
        this.testClass = klass;
        log.info("********************************************************************************");
        log.info(" Camel test runner initiated, testclass: " + klass.getName());
        log.info("********************************************************************************");
    }

    /*
     * Intercepts class level behaviour and adds Camel specific AfterClass teardown.
     * 
     * @see org.junit.runners.ParentRunner#classBlock(org.junit.runner.notification.RunNotifier)
     */
    @Override
    protected Statement classBlock(final RunNotifier notifier) {
        Statement statement = childrenInvoker(notifier);
        statement = withBeforeClasses(statement);
        statement = withAfterClasses(statement);
        statement = withAfterClassTeardown(statement);
        return statement;
    }
    
    /*
     * Intercepts method level behaviour and adds Camel specific setup and teardown.
     * 
     * @see org.junit.runners.BlockJUnit4ClassRunner#methodBlock(org.junit.runners.model.FrameworkMethod)
     */
    @Override
    @SuppressWarnings("deprecation")
    protected Statement methodBlock(FrameworkMethod method) {
        Object test;
        try {
            test = new ReflectiveCallable() {
                @Override
                protected Object runReflectiveCall() throws Throwable {
                    return createTest();
                }
            }.run();
        } catch (Throwable e) {
            return new Fail(e);
        }

        testClassInstance = test;

        Statement statement = methodInvoker(method, test);
        statement = possiblyExpectingExceptions(method, test, statement);
        statement = withPotentialTimeout(method, test, statement);
        statement = withBefores(method, test, statement);
        statement = withSetup(method, statement);
        statement = withAfters(method, test, statement);
        statement = withTeardown(method, statement);

        return statement;
    }
    
    /**
     * @param statement           original statement
     * @return {@link Statement}: Run {@link CamelRunner#tearDownAfterClass()} method
     *                            to clean up after last test.
     */
    private Statement withAfterClassTeardown(Statement statement) {
        return new RunTearDownAfterClass(this, statement);
    }
    
    /**
     * @param method              method being currently tested
     * @param statement           original statement
     * @return {@link Statement}: Run {@link CamelRunner#setUp(FrameworkMethod)} method
     *                            to perform Camel setup before first {@code @Before} method.
     */
    private Statement withSetup(FrameworkMethod method, Statement statement) {
        return new RunSetup(method, this, statement);
    }
    
    /**
     * @param method              method being currently tested
     * @param statement           original statement
     * @return {@link Statement}: Run {@link CamelRunner#tearDown(FrameworkMethod)} method
     *                            to perform teardown after last {@code @After} method.
     */
    private Statement withTeardown(FrameworkMethod method, Statement statement) {
        return new RunTeardown(method, this, statement);
    }
    
    /**
     * Performs setup operations before testing. If {@code @CreateCamelContextPerClass} is used,
     * the whole setup is performed only one for entire test class. Otherwise {@link CamelContext}
     * is created for every test method.
     * 
     * @param method
     * @throws Exception
     */
    public void setUp(FrameworkMethod method) throws Exception {
        log.info("********************************************************************************");
        log.info("Testing: " + method.getName() + "(" + testClass.getName() + ")");
        log.info("********************************************************************************");

        scanner = new AnnotationScanner(testClass);
        scanner.scanForTypeAnnotations();
        
        if (scanner.isCreateCamelContextPerClass()) {
            // test is per class, so only setup once (the first time)
            boolean first = INIT.get() == null;
            if (first) {
                doSetUp();
            } else {
                // and in between tests we must do IoC and reset mocks
                assert context != null;
                scanner.scanForFieldAnnotations(context, testClassInstance);
                postProcessTest();
                resetMocks();
            }
        } else {
            // test is per test so always setup
            doSetUp();
        }

        // only start timing after all the setup
        watch.restart();
    }
    
    /**
     * Does the actual setup.
     * 
     * @throws Exception
     */
    private void doSetUp() throws Exception {
        log.debug("setUp test");
        if (scanner.isDisableJmx()) {
            disableJMX();
        } else {
            enableJMX();
        }

        context = (ModelCamelContext) createCamelContext();
        threadCamelContext.set(context);

        assertNotNull("No context found!", context);

        // reduce default shutdown timeout to avoid waiting for 300 seconds
        context.getShutdownStrategy().setTimeUnit(scanner.getShutdownTimeoutTimeUnit());
        context.getShutdownStrategy().setTimeout(scanner.getShutdownTimeoutValue());

        scanner.scanForMethodAnnotations();
        // set debugger if enabled
        if (scanner.isUseDebugger()) {
            if (context.getStatus().equals(ServiceStatus.Started)) {
                log.info("Cannot setting the Debugger to the starting CamelContext, stop the CamelContext now.");
                // we need to stop the context first to setup the debugger
                context.stop();
            }
            context.setDebugger(new DefaultDebugger());
            addBreakpointsToContextDebugger();
            // note: when stopping CamelContext it will automatically remove the breakpoint
        }

        template = context.createProducerTemplate();
        consumer = context.createConsumerTemplate();
        scanner.scanForFieldAnnotations(context, testClassInstance);
        template.start();
        consumer.start();

        threadTemplate.set(template);
        threadConsumer.set(consumer);

        // enable auto mocking if enabled
        String pattern = scanner.getMockEndpointsValue();
        if (pattern != null) {
            context.addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern));
        }
        pattern = scanner.getMockEndpointsAndSkipValue();
        if (pattern != null) {
            context.addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern, true));
        }

        // configure properties component (mandatory for testing)
        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        Properties extra = useOverridePropertiesWithPropertiesComponent();
        if (extra != null && !extra.isEmpty()) {
            pc.setOverrideProperties(extra);
        }
        Boolean ignore = scanner.isIgnoreMissingLocationWithPropertiesComponent();
        if (ignore != null) {
            pc.setIgnoreMissingLocation(ignore);
        }

        postProcessTest();

        if (scanner.isUseRouteBuilder()) {
            RouteBuilder[] builders = createRouteBuilders();
            for (final RouteBuilder builder : builders) {
                log.debug("Using created route builder: " + builder);
                context.addRoutes(builder);
            }
            replaceFromEndpoints();
            boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
            if (skip) {
                log.info("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
            } else if (scanner.isUseAdviceWith()) {
                log.info("Skipping starting CamelContext as isUseAdviceWith is set to true.");
            } else {
                startCamelContext();
            }
        } else {
            replaceFromEndpoints();
            log.debug("Using route builder from the created context: " + context);
        }
        log.debug("Routing Rules are: " + context.getRoutes());

        assertNotNull("No context found!", context);

        INIT.set(true);
    }
    
    private void replaceFromEndpoints() throws Exception {
        for (final Map.Entry<String, String> entry : fromEndpoints.entrySet()) {
            context.getRouteDefinition(entry.getKey()).adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    replaceFromWith(entry.getValue());
                }
            });
        }
    }
    
    /**
     * Does the actual teardown after last {@code @After} method returns.
     * 
     * @param method     the {@code @Test} method
     * @throws Exception
     */
    public void tearDown(FrameworkMethod method) throws Exception {
        long time = watch.stop();
        
        log.info("********************************************************************************");
        log.info("Testing done: " + method + "(" + getClass().getName() + ")");
        log.info("Took: " + TimeUtils.printDuration(time) + " (" + time + " millis)");
        log.info("********************************************************************************");
        
        if (scanner.isCreateCamelContextPerClass()) {
            // we tear down in after class
            return;
        }
        
        log.debug("tearDown test");
        doStopTemplates(consumer, template);
        doStopCamelContext(context, camelContextService);
    }
    
    /**
     * Does the actual teardown after last {@code @AfterClass} method returns.
     * 
     * @throws Exception
     */
    public void tearDownAfterClass() throws Exception {
        INIT.remove();
        log.debug("tearDownAfterClass test");
        doStopTemplates(threadConsumer.get(), threadTemplate.get());
        doStopCamelContext(threadCamelContext.get(), threadService.get());
    }
    
    private void postProcessTest() throws Exception {
        context = threadCamelContext.get();
        template = threadTemplate.get();
        consumer = threadConsumer.get();
        camelContextService = threadService.get();
        applyCamelPostProcessor(testClassInstance);
    }
    
    /**
     * Applies the {@link DefaultCamelBeanPostProcessor} to this instance.
     *
     * Derived classes using IoC / DI frameworks may wish to turn this into a NoOp such as for CDI
     * we would just use CDI to inject this
     */
    private void applyCamelPostProcessor(Object testClassInstance) throws Exception {
        // use the default bean post processor from camel-core
        DefaultCamelBeanPostProcessor processor = new DefaultCamelBeanPostProcessor(context);
        processor.postProcessBeforeInitialization(testClassInstance, testClass.getName());
        processor.postProcessAfterInitialization(testClassInstance, testClass.getName());
    }
    
    private static void doStopCamelContext(CamelContext context, Service camelContextService) throws Exception {
        if (camelContextService != null) {
            if (camelContextService == threadService.get()) {
                threadService.remove();
            }
            camelContextService.stop();
        } else {
            if (context != null) {
                if (context == threadCamelContext.get()) {
                    threadCamelContext.remove();
                }
                context.stop();
            }
        }
    }

    private static void doStopTemplates(ConsumerTemplate consumer, ProducerTemplate template) throws Exception {
        if (consumer != null) {
            if (consumer == threadConsumer.get()) {
                threadConsumer.remove();
            }
            consumer.stop();
        }
        if (template != null) {
            if (template == threadTemplate.get()) {
                threadTemplate.remove();
            }
            template.stop();
        }
    }
    
    private void startCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.start();
        } else {
            if (context instanceof DefaultCamelContext) {
                DefaultCamelContext defaultCamelContext = (DefaultCamelContext)context;
                if (!defaultCamelContext.isStarted()) {
                    defaultCamelContext.start();
                }
            } else {
                context.start();
            }
        }
    }
    
    private CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        return context;
    }
    
    private JndiRegistry createRegistry() throws Exception {
        Method createRegistry = scanner.getJndiRegistry();
        JndiRegistry newRegistry = new JndiRegistry(createJndiContext());
        if (createRegistry != null)
            newRegistry = (JndiRegistry) createRegistry.invoke(testClassInstance, newRegistry);
        return newRegistry;
    }

    private Context createJndiContext() throws Exception {
        Properties properties = new Properties();

        // jndi.properties is optional
        InputStream in = getClass().getClassLoader().getResourceAsStream("jndi.properties");
        if (in != null) {
            log.debug("Using jndi.properties from classpath root");
            properties.load(in);
        } else {
            properties.put("java.naming.factory.initial", "org.apache.camel.util.jndi.CamelInitialContextFactory");
        }
        return new InitialContext(new Hashtable<Object, Object>(properties));
    }

    /**
     * Calls all methods from {@link CamelRunner#testClass} annotated {@link CreateRouteBuilder}
     * and creates array of {@link RouteBuilder}s.
     * 
     */
    private RouteBuilder[] createRouteBuilders() throws Exception {
        List<Method> methods = scanner.getRouteBuilders();
        if (methods.size() == 0) {
        	if (scanner.isUseRouteBuilder()) {
        		log.warn("No method annotated '@CreateRouteBuilder' was found and "
        				+ "UseRouteBuilder set to 'true'. Use '@UseRouteBuilder(false).'");
        	}
            return new RouteBuilder[0];
        }
        RouteBuilder[] builders = new RouteBuilder[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            builders[i] = (RouteBuilder) methods.get(i).invoke(testClassInstance);
        }
        return builders;
    }
    
    /**
     * Override this method to include and override properties
     * with the Camel {@link PropertiesComponent}.
     *
     * @return additional properties to add/override.
     */
    private Properties useOverridePropertiesWithPropertiesComponent() throws Exception {
        Method method = scanner.getProperties();
        if (method == null) {
            return null;
        }
        Properties properties = (Properties) method.invoke(testClassInstance);
        return properties;
    }
    
    /**
     * Reset all Mock endpoints.
     */
    private void resetMocks() {
        MockEndpoint.resetMocks(context);
    }
    
    /**
     * Disables the JMX agent. Must be called before the {@link #setUp()} method.
     */
    private void disableJMX() {
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");
    }
    
    /**
     * Enables the JMX agent. Must be called before the {@link #setUp()} method.
     */
    private void enableJMX() {
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "false");
    }
    
    /**
     * Invokes all methods from test class annotated with {@link ProvidesBreakpoint}
     * and registers all {@link Breakpoint}s to {@link CamelContext}.
     * 
     * @throws Exception
     */
    private void addBreakpointsToContextDebugger() throws Exception  {
        List<Method> breakpointMethods = scanner.getDebugBreakpoints();
        
        for (final Method method : breakpointMethods)  {
            Breakpoint bp = (Breakpoint) method.invoke(testClassInstance);
            context.getDebugger().addBreakpoint(bp);
            log.info("Adding Breakpoint [{}] to CamelContext with name [{}].", bp, context.getName());
        }
    }
}
