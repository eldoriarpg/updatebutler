package de.eldoria.updatebutler.config;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.eldoria.updatebutler.config.commands.UserCommand;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Getter
public class GuildSettings {
    @Setter
    @Expose
    private String prefix = "+";
    @SerializedName("allowed_users")
    @Expose
    private Set<Long> allowedUsers = new HashSet<>();
    @Expose
    private Map<String, Application> applications = new HashMap<>();

    @Expose
    private Set<UserCommand> userCommands = new HashSet<>();

    public boolean isAllowedUser(Member member) {
        return allowedUsers.contains(member.getIdLong()) || member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR);
    }

    public boolean addAllowedUser(Member user) {
        return allowedUsers.add(user.getIdLong());
    }

    public boolean removeAllowedUser(Member user) {
        return allowedUsers.remove(user.getIdLong());
    }

    public Optional<Application> getApplication(String name) {
        for (var entry : applications.entrySet()) {
            if (entry.getValue().getIdentifier().equalsIgnoreCase(name)) {
                return Optional.ofNullable(entry.getValue());
            }

            if (entry.getKey().equalsIgnoreCase(name)) {
                return Optional.ofNullable(entry.getValue());
            }

            for (String alias : entry.getValue().getAlias()) {
                if (alias.equalsIgnoreCase(name)) {
                    return Optional.ofNullable(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    public void addApplication(String id, Application application) {
        applications.put(id, application);
    }

    public void removeApplication(String name) {
        for (var entry : applications.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                applications.remove(entry.getKey());
                return;
            }
            for (String alias : entry.getValue().getAlias()) {
                if (alias.equalsIgnoreCase(name)) {
                    applications.remove(entry.getKey());
                    return;
                }
            }
        }
    }

    public boolean hasApplication(Member member) {
        for (Application value : applications.values()) {
            if (value.isOwner(member)) return true;
        }
        return false;
    }

    public Optional<UserCommand> getUserCommand(String string) {
        return userCommands.stream().filter(s -> s.isCommand(string)).findFirst();
    }

    public void addUserCommand(UserCommand command) {
        removeCommand(command);
        userCommands.add(command);
    }

    public void removeCommand(UserCommand command) {
        userCommands.remove(command);
    }

    public String getUserCommands() {
        if (userCommands.isEmpty()) return "";
        return userCommands.stream().map(c -> "`" + c.getCommand() + "`").collect(Collectors.joining(", "));
    }

    public String getApplicationCommands() {
        return applications.values()
                .stream()
                .map(a -> "`" + a.getIdentifier()
                        + (a.getAlias().length != 0 ? " (" + String.join(", ", a.getAlias()) + ")" : "") + "`")
                .collect(Collectors.joining(", "));
    }
}
