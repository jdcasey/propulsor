/**
 * Copyright (C) 2015 John Casey (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
  Copyright (C) 2011-2017 Red Hat, Inc. (https://github.com/Commonjava/propulsor)

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package org.commonjava.propulsor.metrics;

import org.commonjava.propulsor.metrics.annotation.Measure;
import org.commonjava.propulsor.metrics.conf.MetricsConfig;
import org.commonjava.propulsor.metrics.spi.TimingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.commonjava.propulsor.metrics.MetricsConstants.EXCEPTION;
import static org.commonjava.propulsor.metrics.MetricsConstants.METER;
import static org.commonjava.propulsor.metrics.MetricsConstants.TIMER;

@Interceptor
@Measure
public class MetricsInterceptor
{

    private static final Logger logger = LoggerFactory.getLogger( MetricsInterceptor.class );

    private final MetricsManager metricsManager;

    private final MetricsConfig config;

    @Inject
    public MetricsInterceptor( MetricsManager manager, MetricsConfig config )
    {
        this.metricsManager = manager;
        this.config = config;
    }

    @AroundInvoke
    public Object operation( InvocationContext context ) throws Exception
    {
        if ( !config.isEnabled() )
        {
            return context.proceed();
        }

        Method method = context.getMethod();
        Measure measure = method.getAnnotation( Measure.class );
        if ( measure == null )
        {
            measure = method.getDeclaringClass().getAnnotation( Measure.class );
        }

        logger.trace( "Gathering metrics for: {}", context.getContextData() );
        String nodePrefix = config.getInstancePrefix();

        String defaultName = metricsManager.getDefaultName( context );

        TimingContext timing = metricsManager.timeAll( Stream.of( measure.timers() )
                                                             .map( n -> metricsManager.getName( nodePrefix, n, defaultName, TIMER ) )
                                                             .collect( Collectors.toSet() ) );


        try
        {
            return context.proceed();
        }
        catch ( Exception e )
        {
            metricsManager.mark( Stream.of( measure.exceptions() )
                                       .map( n -> metricsManager.getName( nodePrefix, n, defaultName, EXCEPTION ) )
                                       .collect( Collectors.toSet() ) );

            throw e;
        }
        finally
        {
            timing.stop();

            metricsManager.mark( Stream.of( measure.meters() )
                                       .map( n -> metricsManager.getName( nodePrefix, n, defaultName, METER ) )
                                       .collect( Collectors.toSet() ) );
        }
    }

}
