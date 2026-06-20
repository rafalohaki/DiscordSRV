/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.objects;

import github.scarsz.discordsrv.DiscordSRV;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Helper for config-driven map reloads — extracted from 4 duplicated
 * {@code reload*()} methods in {@link DiscordSRV}.
 *
 * <p>Java 25 sealed utility class — not instantiable.
 */
public final class ConfigReloadHelper {

    private ConfigReloadHelper() {}

    /**
     * Clears the map and reloads it from a config section, applying the
     * given key/value extractor to each child dynamic node.
     *
     * @param map       the map to clear and populate
     * @param configKey the config path (passed to {@code config().dget()})
     * @param keyTransform optional transform on the key (e.g. {@code String::toLowerCase}); null for identity
     * @param loader    receives (key, value) for each child node
     */
    public static void reloadMapFromConfig(
            Map<String, String> map,
            String configKey,
            java.util.function.UnaryOperator<String> keyTransform,
            BiConsumer<String, String> loader
    ) {
        map.clear();
        DiscordSRV.config().dget(configKey).children().forEach(dynamic -> {
            String key = dynamic.key().convert().intoString();
            if (keyTransform != null) key = keyTransform.apply(key);
            String value = dynamic.convert().intoString();
            loader.accept(key, value);
        });
    }

    /**
     * Clears the regex map and reloads it from a config section.
     * Each child key is compiled as a {@link Pattern} (DOTALL flag);
     * invalid patterns are logged and skipped.
     *
     * @param map       the regex map to clear and populate
     * @param configKey the config path
     */
    public static void reloadRegexMap(Map<Pattern, String> map, String configKey) {
        map.clear();
        DiscordSRV.config().dget(configKey).children().forEach(d -> {
            String key = d.key().convert().intoString();
            if (StringUtils.isEmpty(key)) return;
            try {
                Pattern pattern = Pattern.compile(key, Pattern.DOTALL);
                map.put(pattern, d.convert().intoString());
            } catch (PatternSyntaxException e) {
                DiscordSRV.error("Invalid regex pattern: " + key + " (" + e.getDescription() + ")");
            }
        });
    }
}
