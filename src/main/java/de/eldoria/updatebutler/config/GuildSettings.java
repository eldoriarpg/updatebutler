package de.eldoria.updatebutler.config;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Member;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


@Getter
public class GuildSettings {
    @Setter
    private String prefix = "+";
    @SerializedName("allowed_users")
    private Set<Long> allowedUsers = new HashSet<>();
    private HashMap<String, Application> applications = new HashMap<>();

    public boolean isAllowedUser(Member user) {
        return allowedUsers.contains(user.getIdLong());
    }

    public boolean addAllowedUser(Member user) {
        return allowedUsers.add(user.getIdLong());
    }

    public boolean removeAllowedUser(Member user) {
        return allowedUsers.remove(user.getIdLong());
    }

    public Optional<Application> getApplication(String name) {
        for (var entry : applications.entrySet()) {
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
}
