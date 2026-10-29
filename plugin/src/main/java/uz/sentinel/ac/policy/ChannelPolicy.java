package uz.sentinel.ac.policy;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Client-declared channels are policy signals, not proof of installed mods. */
public final class ChannelPolicy {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9/._-]{1,128}");
    private final Set<String> channels;
    private final Set<String> namespaces;

    public ChannelPolicy(Collection<String> channels, Collection<String> namespaces) {
        this.channels = validated(channels, CHANNEL);
        this.namespaces = validated(namespaces, NAMESPACE);
    }

    private static Set<String> validated(Collection<String> entries, Pattern pattern) {
        return entries.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).peek(s -> {
            if (!pattern.matcher(s).matches()) throw new IllegalArgumentException("Invalid channel policy entry: " + s);
        }).collect(Collectors.toUnmodifiableSet());
    }

    public Optional<String> match(String channel) {
        if (channel == null || channel.length() > 193) return Optional.empty();
        String normalized = channel.toLowerCase(Locale.ROOT);
        if (!CHANNEL.matcher(normalized).matches()) return Optional.empty();
        if (channels.contains(normalized)) return Optional.of(normalized);
        String namespace = normalized.substring(0, normalized.indexOf(':'));
        return namespaces.contains(namespace) ? Optional.of(namespace + ":*") : Optional.empty();
    }
}
