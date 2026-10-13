package cn.v7soft.admin.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the legacy workspace, including directories no longer registered in the database. */
public final class NginxCertificateReferences {
    private NginxCertificateReferences() {}

    public static List<Path> files(Path root) throws IOException {
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted().toList();
            for (Path path : paths) {
                // Do not silently miss references behind symlinks or outside this workspace.
                if (Files.isSymbolicLink(path)) throw new IOException("nginx 工作区存在符号链接: " + path);
            }
            return paths.stream().filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
        }
    }

    public static List<Path> certificates(Path config) throws IOException {
        List<String> tokens = tokenize(Files.readString(config));
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!token.equals("ssl_certificate") && !token.equals("ssl_certificate_key")
                    && !token.equals("ssl_trusted_certificate")) continue;
            if (i + 2 >= tokens.size() || !tokens.get(i + 2).equals(";")) {
                throw new IOException("证书指令无法解析: " + config);
            }
            String value = tokens.get(i + 1);
            Path path = Path.of(value);
            if (value.contains("$") || (!path.isAbsolute() && !value.startsWith("/"))) {
                throw new IOException("旧版 nginx 配置的证书路径无法静态校验: " + config);
            }
            paths.add(path.toAbsolutePath().normalize());
        }
        return paths;
    }

    // Nginx comments only start outside quoted strings. Preserve quoted # and whitespace in paths.
    private static List<String> tokenize(String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                if (++i == text.length()) throw new IOException("nginx 配置含不完整转义");
                word.append(text.charAt(i));
            } else if (quote != 0) {
                if (ch == quote) quote = 0;
                else word.append(ch);
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (Character.isWhitespace(ch) || ch == ';' || ch == '{' || ch == '}' || ch == '#') {
                if (!word.isEmpty()) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                if (ch == '#') {
                    while (i + 1 < text.length() && text.charAt(i + 1) != '\n') i++;
                } else if (!Character.isWhitespace(ch)) tokens.add(String.valueOf(ch));
            } else word.append(ch);
        }
        if (quote != 0) throw new IOException("nginx 配置引号未闭合");
        if (!word.isEmpty()) tokens.add(word.toString());
        return tokens;
    }

    public static void assertUnreferenced(Path nginxRoot, Path certDir) throws IOException {
        assertUnreferenced(nginxRoot, List.of(certDir));
    }

    public static void assertUnreferenced(Path nginxRoot, Collection<Path> certDirs) throws IOException {
        Set<Path> targets = certDirs.stream().map(p -> p.toAbsolutePath().normalize()).collect(Collectors.toSet());
        for (Path config : files(nginxRoot)) {
            for (Path cert : certificates(config)) {
                for (Path parent = cert; parent != null; parent = parent.getParent()) {
                    if (targets.contains(parent)) {
                        throw new IOException("证书仍被 nginx 配置引用，禁止删除: " + config);
                    }
                }
            }
        }
    }

    public static void validate(Path nginxRoot) throws IOException {
        for (Path config : files(nginxRoot)) {
            for (Path cert : certificates(config)) {
                if (!Files.isRegularFile(cert) || !Files.isReadable(cert) || Files.size(cert) == 0) {
                    throw new IOException("nginx 配置引用的证书不存在、不可读或为空: " + config + " -> " + cert);
                }
            }
        }
    }
}
