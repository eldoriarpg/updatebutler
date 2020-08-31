package de.eldoria.updatebutler.util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Verifier {
    private static final Pattern ID_PATTERN = Pattern.compile("(?:<[@#!&]{1,2})?(?<id>[0-9]{18})(?:>)?");
    private static final Pattern IPV_4 = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(:[0-9]{1,5})?$");
    private static final Pattern IPV_6 = Pattern.compile("(^\\[([a-fA-F0-9]{0,4}:){4,7}[a-fA-F0-9]{0,4}]:"
            + "[0-9]{1,5}$)|(([a-fA-F0-9]{0,4}:){4,7}[a-fA-F0-9]{0,4}$)");
    private static final Pattern DOMAIN = Pattern.compile("^(?!://)([a-zA-Z0-9-_]+\\.)*[a-zA-Z0-9][a-zA-Z0-9-_]"
            + "+\\.[a-zA-Z]{2,11}?(:[0-9]{1,5})?$");


    /**
     * Returns true if the id is a valid id.
     *
     * @param id id to test.
     * @return true if id is valid
     */
    public static boolean isValidId(String id) {
        return getIdRaw(id).length() == 18;
    }

    /**
     * Tries to cast a String to a boolean.
     * Returns null if not possible
     *
     * @param string String to parse
     * @return boolean or null if string can't be parsed
     */
    public static Optional<Boolean> checkAndGetBoolean(String string) {
        Boolean state = null;
        if ("true".equalsIgnoreCase(string) || "false".equalsIgnoreCase(string)) {
            state = "true".equalsIgnoreCase(string);
        }
        return Optional.ofNullable(state);
    }

    /**
     * Check if two object have the same Snowflake.
     *
     * @param a first object
     * @param b second object
     * @return true if the snowflakes are equal.
     */
    public static boolean equalSnowflake(ISnowflake a, ISnowflake b) {
        if (a == null || b == null) return false;
        return a.getIdLong() == b.getIdLong();
    }

    /**
     * Returns from a list of role ids all valid roles.
     *
     * @param guild guild for role lookup
     * @param args  array of role id
     * @return list of valid roles
     */
    public static List<Role> getValidRoles(Guild guild, List<String> args) {
        return getValidRoles(guild, args.toArray(String[]::new));
    }

    /**
     * Returns from a list of role ids all valid roles.
     *
     * @param guild guild for role lookup
     * @param args  array of role ids
     * @return list of valid roles
     */
    public static List<Role> getValidRoles(Guild guild, String[] args) {
        return Arrays.stream(args).map(roleId -> guild.getRoleById(getIdRaw(roleId)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Returns from a list of text channels ids all valid channels.
     *
     * @param guild guild for role lookup
     * @param args  array of role id
     * @return list of valid roles
     */
    public static List<TextChannel> getValidTextChannels(Guild guild, List<String> args) {
        return getValidTextChannels(guild, args.toArray(String[]::new));
    }

    /**
     * Returns from a list of text channel ids all valid channels.
     *
     * @param guild guild for channel lookup
     * @param args  array of channel ids
     * @return list of valid channels
     */
    private static List<TextChannel> getValidTextChannels(Guild guild, String[] args) {
        return Arrays.stream(args).map(channelId -> guild.getTextChannelById(getIdRaw(channelId)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get the valid users by id.
     *
     * @param shardManager jda for user lookup
     * @param collect      list of long ids
     * @return list of valid users.
     */
    public static List<User> getValidUserByLong(ShardManager shardManager, List<Long> collect) {
        return collect.stream()
                .map(shardManager::getUserById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Returns true if on of the arguments matches the argument. Not case sensitive.
     *
     * @param argument argument to match against.
     * @param args     one or more arguments to check.
     * @return true if one argument matches.
     */
    public static boolean isArgument(String argument, String... args) {
        for (String arg : args) {
            if (argument.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts an id from discord's formatting.
     *
     * @param id the formatted id.
     * @return the extracted id.
     */
    public static String getIdRaw(String id) {
        Matcher matcher = ID_PATTERN.matcher(id);
        if (!matcher.matches()) {
            return "0";
        }
        return matcher.group(1);
    }

}
