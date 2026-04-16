package cn.v7soft.core.annotation;

import cn.v7soft.core.utils.V7IdentifierGenerator;
import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@IdGeneratorType(V7IdentifierGenerator.class)
@Retention(RUNTIME) @Target({METHOD,FIELD})
public @interface V7IdSequence {
}
