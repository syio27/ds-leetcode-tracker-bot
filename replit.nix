{
  description = "Java Discord Bot";
  deps = [
    pkgs.graalvm17-ce
    pkgs.gradle
    pkgs.replitPackages.jdt-language-server
    pkgs.replitPackages.java-debug
  ];
} 