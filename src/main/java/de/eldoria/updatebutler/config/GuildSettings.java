package de.eldoria.updatebutler.config;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.eldoria.updatebutler.config.commands.UserCommand;
import de.eldoria.updatebutler.config.phrase.Phrase;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Data
public class GuildSettings {
    @SerializedName("allowed_users")
    @Expose
    private final Set<Long> allowedUsers = new HashSet<>();
    @Expose
    private final Map<String, Application> applications = new HashMap<>();
    @Expose
    private final Set<UserCommand> userCommands = new HashSet<>();
    @Expose
    private final List<Phrase> phrases = new ArrayList<>();
    @Setter
    @Expose
    private String prefix = "+";
    @Setter
    @Expose
    private long timeChannel = 0L;
    @Setter
    @Expose
    private String timeZone = "";

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

    public void addPhrase(Phrase phrase) {
        phrases.add(phrase);
    }

    public Optional<Phrase> removePhrase(int index) {
        if (index >= phrases.size()) return Optional.empty();
        Phrase phrase = phrases.get(index);
        phrases.remove(index);
        return Optional.ofNullable(phrase);
    }

    public String getPhrases() {
        AtomicInteger integer = new AtomicInteger(0);
        return phrases.stream()
                .map(a -> integer.incrementAndGet() + " (" + a.getCommand() + ")```" + a.getPhrase() + "```")
                .collect(Collectors.joining("\n"));
    }

    public Optional<Phrase> matchPhrase(String content) {
        return phrases.stream().filter(p -> p.matches(content)).findFirst();
    }
}
