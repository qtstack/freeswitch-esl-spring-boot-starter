package io.github.qtstack.fsesl.spring.boot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link EslEventHandler} bean with the FreeSWITCH event names it handles.
 *
 * <p>An annotation without values or with an empty array matches every event.</p>
 *
 * <p>Use {@code subclass} for CUSTOM events. A subclass implies {@code CUSTOM} when no event
 * name is supplied, for example {@code @EslEventName(subclass = "callcenter::info")}. Use
 * {@code @EslEventName("CUSTOM")} to receive every CUSTOM event.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EslEventName {

    String[] value() default {};

    String[] subclass() default {};
}
