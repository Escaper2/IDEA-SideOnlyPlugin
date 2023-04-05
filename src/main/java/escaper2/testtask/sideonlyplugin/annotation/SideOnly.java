package escaper2.testtask.sideonlyplugin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface SideOnly {
    Side[] value();
}
