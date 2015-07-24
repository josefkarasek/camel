package org.apache.camel.test.junit4.annotation;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.util.ReflectionHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Scans the {@link AnnotationScanner#testClass} for annotations and processes them.
 * Fields annotated with some of camel.test46.Annotations have their
 * dependencies injected and similarly annotated methods are invoked
 * and corresponding results are stored for later use inside runner. 
 * 
 * @author <a href="mailto:karasek.jose@gmail.com">Josef Karásek</a>
 *
 */
public class AnnotationScanner {
    private Class<?> testClass;
    private boolean isUseAdviceWith;
    private boolean isMockEndpoints;
    private boolean isUseRouteBuilder = true;
    private boolean disableJmx;
    private boolean isUseDebugger;
    private boolean isMockEndpointsAndSkip;
    private boolean createCamelContextPerClass;
    private boolean isIgnoreMissingLocation;
    private String useAdvicewithValue;
    private String mockEndpointsValue;
    private String mockEndpointsAndSkipValue;
    private int shutdownTimeoutValue = 10;
    private TimeUnit shutdownTimeoutTimeUnit = TimeUnit.SECONDS;
    private List<Method> routeBuilders = new LinkedList<>();
    private List<Method> debugBreakpoints = new LinkedList<>();
    private Method overridePropertiesMethod;
    private Method jndiRegistryMethod;

    /**
     * @param testClass the class under test.
     */
    public AnnotationScanner(Class<?> testClass) {
        assert testClass != null;
        this.testClass = testClass;
    }
    
    /**
     * Scans for annotations present on the {@link AnnotationScanner#testClass}.
     */
    public void scanForTypeAnnotations() {
        
        Annotation[] annotations = testClass.getDeclaredAnnotations();
        for(Annotation currentAnnotation : annotations) {
            
            if(currentAnnotation instanceof UseAdviceWith) {
                isUseAdviceWith = ((UseAdviceWith) currentAnnotation).value();
            } else if(currentAnnotation instanceof DisableJmx) {
                disableJmx = ((DisableJmx) currentAnnotation).value();
            } else if(currentAnnotation instanceof UseDebugger) {
                isUseDebugger = ((UseDebugger) currentAnnotation).value();
            } else if(currentAnnotation instanceof CreateCamelContextPerClass) {
                createCamelContextPerClass = ((CreateCamelContextPerClass) currentAnnotation).value();
            } else if(currentAnnotation instanceof MockEndpoints) {    
                isMockEndpoints = true;
                mockEndpointsValue = ((MockEndpoints) currentAnnotation).value();
            } else if(currentAnnotation instanceof UseRouteBuilder) {
                isUseRouteBuilder = ((UseRouteBuilder) currentAnnotation).value();
            } else if(currentAnnotation instanceof IgnoreMissingLocation) {
                isIgnoreMissingLocation = ((IgnoreMissingLocation) currentAnnotation).value();
            } else if(currentAnnotation instanceof MockEndpointsAndSkip) {
                isMockEndpointsAndSkip = true;
                mockEndpointsAndSkipValue = ((MockEndpointsAndSkip) currentAnnotation).value();
            } else if(currentAnnotation instanceof ShutdownTimeout) {
                shutdownTimeoutValue = ((ShutdownTimeout) currentAnnotation).value();
                shutdownTimeoutTimeUnit = ((ShutdownTimeout) currentAnnotation).timeUnit();
            }
        }
    }
    
    /**
     * Scans for field annotations and injects dependencies.
     */
    public void scanForFieldAnnotations(CamelContext context, Object testInstance) {
        assert context != null;
        
        Field[] fields = testClass.getDeclaredFields();
        for(Field currentField : fields) {
            Annotation[] annotations = currentField.getAnnotations();
            for(Annotation currentAnnotation : annotations) {
                if(currentAnnotation instanceof ContextInject) {
                    if(!Modifier.isPublic(currentField.getModifiers())) {
                        throw new IllegalArgumentException("Field [" + currentField.getName()
                                + "] is annotated with ContextInject but is not public.");
                    }
                    ReflectionHelper.setField(currentField, testInstance, context);
                } 
            }
        }
    }
    
    /**
     * Scans for method annotations.
     */
    public void scanForMethodAnnotations() {
        
        Method[] methods = testClass.getDeclaredMethods();
        for(Method currentMethod : methods) {
            if(currentMethod.getAnnotation(CreateRouteBuilder.class) != null) {
                handleCreateRouteBuilder(currentMethod);
            } else if(currentMethod.getAnnotation(UseOverridePropertiesWithPropertiesComponent.class) != null) {
                handleOverrideProperties(currentMethod);
            } else if(currentMethod.getAnnotation(CreateRegistry.class) != null) {
                handleCreateRegistry(currentMethod);
            } else if(currentMethod.getAnnotation(ProvidesBreakpoint.class) != null) {
                handleProvidesBreakpoint(currentMethod);
            }
        }
    }
    
    /**
     * All methods annotated with {@link CreateRouteBuilder} are stored for creating
     * {@link RouteBuilder}s in {@link CamelRunner}.
     * 
     * @param method     current method
     * @param annotation {@link CreateRouteBuilder} annotation
     */
    private void handleCreateRouteBuilder(Method method) {
        Class<?>[] argTypes = method.getParameterTypes();
        if(argTypes.length != 0) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRouteBuilder but is not a no-argument method.");
        } else if(!RouteBuilder.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRouteBuilder but does not return a RouteBuilder.");
        } else if(!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRouteBuilder but is not public.");
        } else if(Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRouteBuilder but is static.");
        }
        routeBuilders.add(method);
    }
    
    /**
     * Method annotated with {@link UseOverridePropertiesWithPropertiesComponent} is stored for creating
     * {@link Properties} in {@link CamelRunner}.
     * 
     * @param method     current method
     * @param annotation {@link UseOverridePropertiesWithPropertiesComponent} annotation
     */
    private void handleOverrideProperties(Method method) {
        Class<?>[] argTypes = method.getParameterTypes();
        if(argTypes.length != 0) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with OverridePropertiesWithPropertiesComponent but is not a no-argument method.");
        } else if(!Properties.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with OverridePropertiesWithPropertiesComponent but does not return a Properties.");
        } else if(!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with OverridePropertiesWithPropertiesComponent but is not public.");
        } else if(Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with OverridePropertiesWithPropertiesComponent but is static.");
        }
        overridePropertiesMethod = method;
    }
    
    /**
     * Method annotated with {@link CreateRegistry} is stored for creating
     * {@link JndiRegistry} in {@link CamelRunner}.
     * 
     * @param method     current method
     * @param annotation {@link CreateRegistry} annotation
     */
    private void handleCreateRegistry(Method method) {
        Class<?>[] argTypes = method.getParameterTypes();
        if(argTypes.length != 1) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRegistry but doesn't declare one argument of type JndiRegistry.");
        } else if(!JndiRegistry.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRegistry but does not return a JndiRegistry.");
        } else if(!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRegistry but is not public.");
        } else if(Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRegistry but is static.");
        }
        if(!JndiRegistry.class.isAssignableFrom(argTypes[0])) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with CreateRegistry but doesn't declare one argument "
                    + "of type JndiRegistry");
        }
        jndiRegistryMethod = method;
    }
    
    /**
     * All methods annotated with {@link ProvidesBreakpoint} are stored for creating
     * {@link Breakpoint}s in {@link CamelRunner}.
     * 
     * @param method     current method
     * @param annotation {@link ProvidesBreakpoint} annotation
     */
    private void handleProvidesBreakpoint(Method method) {
        Class<?>[] argTypes = method.getParameterTypes();
        if(argTypes.length != 0) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with ProvidesBreakpoint but is not a no-argument method.");
        } else if (!Breakpoint.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with ProvidesBreakpoint but does not return a Breakpoint.");
        } else if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with ProvidesBreakpoint but is not public.");
        } else if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method [" + method.getName()
                    + "] is annotated with ProvidesBreakpoint but is static.");
        }
        debugBreakpoints.add(method);
    }

    /**
     * @return the isUseRouteBuilder
     */
    public boolean isUseRouteBuilder() {
        return isUseRouteBuilder;
    }
    
    /**
     * @return the isMockEndpoints
     */
    public boolean isMockEndpoints() {
        return isMockEndpoints;
    }

    /**
     * @return the isUseAdviceWith
     */
    public boolean isUseAdviceWith() {
        return isUseAdviceWith;
    }
    
    /**
     * @return the disableJmx
     */
    public boolean isDisableJmx() {
        return disableJmx;
    }

    /**
     * @return the isUseDebugger
     */
    public boolean isUseDebugger() {
        return isUseDebugger;
    }

    /**
     * @return the isMockEndpointsAndSkip
     */
    public boolean isMockEndpointsAndSkip() {
        return isMockEndpointsAndSkip;
    }

    /**
     * @return the useAdvicewithValue
     */
    public String getUseAdvicewithValue() {
        return useAdvicewithValue;
    }

    /**
     * @return the mockEndpointsValue
     */
    public String getMockEndpointsValue() {
        return mockEndpointsValue;
    }

    /**
     * @return the mockEndpointsAndSkipValue
     */
    public String getMockEndpointsAndSkipValue() {
        return mockEndpointsAndSkipValue;
    }

    /**
     * @return the shutdownTimeoutValue
     */
    public int getShutdownTimeoutValue() {
        return shutdownTimeoutValue;
    }

    /**
     * @return the shutdownTimeoutTimeUnit
     */
    public TimeUnit getShutdownTimeoutTimeUnit() {
        return shutdownTimeoutTimeUnit;
    }

    /**
     * @return the createCamelContextPerClass
     */
    public boolean isCreateCamelContextPerClass() {
        return createCamelContextPerClass;
    }
    
    /**
     * @return the routeBuilders
     */
    public List<Method> getRouteBuilders() {
        return routeBuilders;
    }
    
    /**
     * @return the properties
     */
    public Method getProperties() {
        return overridePropertiesMethod;
    }
    
    /**
     * @return the jndiRegistry
     */
    public Method getJndiRegistry() {
        return jndiRegistryMethod;
    }

    /**
     * @return the isIgnoreMissingLocation
     */
    public boolean isIgnoreMissingLocationWithPropertiesComponent() {
        return isIgnoreMissingLocation;
    }
    
    /**
     * @return the debugBreakpoints
     */
    public List<Method> getDebugBreakpoints() {
        return debugBreakpoints;
    }
}

