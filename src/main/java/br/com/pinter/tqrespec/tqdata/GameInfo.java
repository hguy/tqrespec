/*
 * Copyright (C) 2019 Emerson Pinter - All Rights Reserved
 */

/*    This file is part of TQ Respec.

    TQ Respec is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    TQ Respec is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with TQ Respec.  If not, see <http://www.gnu.org/licenses/>.
*/

package br.com.pinter.tqrespec.tqdata;

import br.com.pinter.tqrespec.util.Constants;
import com.sun.jna.platform.win32.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GameInfo {
    private static final boolean DBG = false;

    private static GameInfo instance = null;

    private String gamePath = null;

    private GameInfo() {
    }

    public static GameInfo getInstance() {
        if (instance == null) {
            synchronized (GameInfo.class) {
                if (instance == null)
                    instance = new GameInfo();
            }
        }
        return instance;
    }

    @SuppressWarnings("unused")
    public String getWindowsVersion() {
        int major = Advapi32Util.registryGetIntValue(
                WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "CurrentMajorVersionNumber");
        int minor = Advapi32Util.registryGetIntValue(
                WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "CurrentMinorVersionNumber");
        return String.format("%d.%d", major, minor);
    }

    private boolean gamePathExists(Path path) {
        Path databasePath = Paths.get(path.toString(), "Database");
        if (Files.exists(databasePath) && Files.isDirectory(databasePath)) {
            return true;
        }

        return false;
    }

    private Path getGameSteamPath() {
        Path steamLibraryPath = getSteamLibraryPath();
        if (steamLibraryPath != null) {
            Path gamePath = Paths.get(steamLibraryPath.toString(), "common",
                    Constants.GAME_DIRECTORY_STEAM).toAbsolutePath();
            if(gamePathExists(gamePath)) {
                return gamePath;
            }
        }
        return null;
    }

    private Path getSteamLibraryPath() {
        try {
            String steamPath = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER, "SOFTWARE\\Valve\\Steam", "SteamPath");

            Path steamappsPath = Paths.get(steamPath, "SteamApps").toAbsolutePath();
            Path steamGamePath = Paths.get(steamappsPath.toString(), "common",
                    Constants.GAME_DIRECTORY_STEAM).toAbsolutePath();
            if (gamePathExists(steamGamePath)) {
                return steamappsPath;
            }

            Pattern regexOuter = Pattern.compile(".*LibraryFolders.*\\{(.*)}.*", Pattern.DOTALL);
            Pattern regexInner = Pattern.compile("\\s*\"\\d\"\\s+\"([^\"]+)\".*");

            ArrayList<String> libraryFolderList = new ArrayList<>();
            String steamConfig = Files.readString(Paths.get(steamappsPath.toString(), "libraryfolders.vdf"));

            Matcher outer = regexOuter.matcher(steamConfig);
            if (outer.find()) {
                String content = outer.group(1);
                Matcher inner = regexInner.matcher(content);
                while (inner.find()) {
                    if (inner.group(1) != null && !inner.group(1).isEmpty()) {
                        libraryFolderList.add(inner.group(1));
                    }
                }
            }

            for (String libraryFolder : libraryFolderList) {
                Path libraryPath = Paths.get(libraryFolder, "SteamApps").toAbsolutePath();
                Path libraryGamePath = Paths.get(libraryPath.toString(), "common",
                        Constants.GAME_DIRECTORY_STEAM).toAbsolutePath();
                if (gamePathExists(libraryGamePath)) {
                    return libraryPath;
                }
            }
        } catch (Exception e) {
            if (DBG) e.printStackTrace();
        }
        return null;
    }

    private Path getGameGogPath() {
        try {
            String gog64bit = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\GOG.com\\Games\\1196955511", "PATH");
            if (StringUtils.isNotEmpty(gog64bit)) {
                Path gog64bitPath = Paths.get(gog64bit).toAbsolutePath();
                if (gamePathExists(gog64bitPath)) {
                    return gog64bitPath;
                }
            }
        } catch (Exception e) {
        }

        try {
            String gog32bit = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\Wow6432Node\\GOG.com\\Games\\1196955511", "PATH");
            Path gog32bitPath = Paths.get(gog32bit).toAbsolutePath();
            if (gamePathExists(gog32bitPath)) {
                return gog32bitPath;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Path getGameDiscPath() {
        try {
            String disc = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\Iron Lore\\Titan Quest Immortal Throne", "Install Location");
            if (StringUtils.isNotBlank(disc)) {
                Path discPath = Paths.get(disc).toAbsolutePath();
                if (gamePathExists(discPath)) {
                    return discPath;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Path getGameInstalledPath(String regexGameName) {
        String[] installedApps = new String[0];
        try {
            installedApps = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE,
                    "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall");
        } catch (Exception e) {
            if (DBG) e.printStackTrace();
        }

        for (String app : installedApps)
            try {
                String appDisplayName = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                        "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\" + app, "DisplayName");
                if (appDisplayName.matches(regexGameName)) {
                    if (DBG) System.err.println("Installed: displayname found -- " + regexGameName);
                    String installed = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\" + app, "InstallLocation");
                    Path installedPath = Paths.get(installed).toAbsolutePath();
                    if (gamePathExists(installedPath)) {
                        return installedPath;
                    }
                } else {
                    if (DBG)
                        System.err.println("Installed: displayname not found --- " + regexGameName + "---" + appDisplayName);
                }
            } catch (Exception e) {
                if (DBG) e.printStackTrace();
            }
        return null;
    }

    private String detectGamePath() {
        Path installedPath = getGameInstalledPath(Constants.REGEX_REGISTRY_INSTALL);
        if (installedPath != null && gamePathExists(installedPath)) {
            if (DBG) System.err.println("Installed: found");
            return installedPath.toString();
        }

        Path gameSteam = getGameSteamPath();
        if (gameSteam != null && gamePathExists(gameSteam)) {
            if (DBG) System.err.println("SteamLibrary: found");
            return gameSteam.toString();
        }

        Path gogPath = getGameGogPath();
        if (gogPath != null && gamePathExists(gogPath)) {
            if (DBG) System.err.println("Gog: found");
            return gogPath.toString();
        }

        Path installedPathFallback = getGameInstalledPath(Constants.REGEX_REGISTRY_INSTALL_FALLBACK);
        if (installedPathFallback != null && gamePathExists(installedPathFallback)) {
            if (DBG) System.err.println("Installed: found");
            return installedPathFallback.toString();
        }

        Path discPath = getGameDiscPath();
        if (discPath != null && gamePathExists(discPath)) {
            if (DBG) System.err.println("Disc: found");
            return discPath.toString();
        }
        return null;
    }

    public String getGamePath() throws FileNotFoundException {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return Constants.DEV_GAMEDATA;
        }

        if (StringUtils.isEmpty(gamePath)) {
            gamePath = detectGamePath();
        }

        if (StringUtils.isEmpty(gamePath)) {
            if (gamePathExists(Paths.get(Constants.DEV_GAMEDATA))) {
                gamePath = Constants.DEV_GAMEDATA;
            } else if (gamePathExists(Paths.get(Constants.PARENT_GAMEDATA))) {
                gamePath = Constants.PARENT_GAMEDATA;
            } else {
                throw new FileNotFoundException("Game path not detected");
            }
        }

        return gamePath;
    }

    public String getSavePath() {
        String userHome = System.getProperty("user.home");
        String subdirectory = File.separator + Paths.get("My Games", "Titan Quest - Immortal Throne").toString();

        if (DBG || !SystemUtils.IS_OS_WINDOWS) {
            System.err.println("SavePath: user.home is " + userHome);
        }

        String saveDirectory;
        try {
            saveDirectory = Shell32Util.getFolderPath(ShlObj.CSIDL_MYDOCUMENTS);
        } catch (Exception e) {
            saveDirectory = userHome;
        }

        Path savePath = Paths.get(saveDirectory + subdirectory);
        if (Files.exists(savePath)) {
            if (DBG) System.err.println("SavePath: found");
            return savePath.toAbsolutePath().toString();
        }
        return null;
    }

    public String getSaveDataMainPath() {
        if (!SystemUtils.IS_OS_WINDOWS) return Paths.get(Constants.DEV_GAMEDATA, "SaveData", "Main").toString();
        String savePath = getSavePath();
        if (StringUtils.isNotEmpty(savePath)) {
            return Paths.get(savePath, "SaveData", "Main").toString();
        }
        return null;
    }

    public String getSaveDataUserPath() {
        if (!SystemUtils.IS_OS_WINDOWS) return Paths.get(Constants.DEV_GAMEDATA, "SaveData", "User").toString();
        String savePath = getSavePath();
        if (StringUtils.isNotEmpty(savePath)) {
            return Paths.get(savePath, "SaveData", "User").toString();
        }
        return null;
    }

    public String[] getPlayerListMain() throws Exception {
        String savePath = this.getSaveDataMainPath();
        File directory = new File(savePath);
        ArrayList<String> playerList = new ArrayList<>();
        if (directory.exists()) {
            for (File player : Objects.requireNonNull(directory.listFiles((File fileName) -> fileName.getName().startsWith("_")))) {
                playerList.add(player.getName().replaceAll("^_", ""));
            }
        } else {
            throw new Exception("No player found");
        }
        String[] ret = new String[playerList.size()];
        playerList.toArray(ret);
        return ret;
    }
}
