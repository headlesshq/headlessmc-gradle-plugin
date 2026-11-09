{
  description = "Development shell for the HeadlessMc Gradle plugins";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { nixpkgs, ... }:
    let
      inherit (nixpkgs) lib;

      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      forAllSystems = f: lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});

      # 25 builds the plugins and runs HeadlessMc, the examples and functional tests use 17 and 21 toolchains
      jdks = pkgs: {
        "17" = pkgs.temurin-bin-17;
        "21" = pkgs.temurin-bin-21;
        "25" = pkgs.temurin-bin-25;
      };

      mkJavaShell =
        pkgs: primary:
        let
          available = jdks pkgs;
          jdk = available.${primary};
        in
        pkgs.mkShell (
          {
            packages = [ jdk ];

            JAVA_HOME = jdk.home;
          }
          // lib.optionalAttrs (lib.length (lib.attrNames available) > 1) {
            # Gradle ignores org.gradle.java.installations.paths in GRADLE_OPTS,
            # but its toolchain detection scans $SDKMAN_CANDIDATES_DIR/java/*
            SDKMAN_CANDIDATES_DIR = pkgs.linkFarm "jdks" (
              lib.mapAttrs' (version: j: lib.nameValuePair "java/${version}" j.home) available
            );
          }
        );
    in
    {
      devShells = forAllSystems (pkgs: {
        default = mkJavaShell pkgs "25";
        java17 = mkJavaShell pkgs "17";
        java21 = mkJavaShell pkgs "21";
      });

      formatter = forAllSystems (pkgs: pkgs.nixfmt-tree);
    };
}
