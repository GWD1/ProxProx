package io.gomint.proxprox.api.config;

import java.lang.annotation.*;

@Retention( RetentionPolicy.RUNTIME )
@Target( ElementType.FIELD )
@Repeatable( Comments.class )
public @interface Comment {
    String value() default "";
}
