package org.eclipse.mat.report;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.eclipse.mat.query.IResult;

@Target( { TYPE })
@Retention(RUNTIME)
public @interface Renderer
{
    Class<? extends IResult>[] result() default IResult.class;

    String target();
}
