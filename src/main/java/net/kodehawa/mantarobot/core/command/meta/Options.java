/*
 * Copyright (C) 2016 Kodehawa
 *
 * Mantaro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Mantaro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro. If not, see http://www.gnu.org/licenses/
 *
 */

package net.kodehawa.mantarobot.core.command.meta;

import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Options {
    Option[] value() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Option {
        OptionType type();
        String name();
        String description();
        boolean required()
                default false;
        int minValue()
                default 1;
        int maxValue()
                default Integer.MAX_VALUE;
        Choice[] choices() default {};
    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Choice {
        String description();
        String value();
    }
}
