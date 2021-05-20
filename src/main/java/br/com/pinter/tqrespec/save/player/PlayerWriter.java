/*
 * Copyright (C) 2021 Emerson Pinter - All Rights Reserved
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

package br.com.pinter.tqrespec.save.player;

import br.com.pinter.tqrespec.Settings;
import br.com.pinter.tqrespec.core.State;
import br.com.pinter.tqrespec.core.UnhandledRuntimeException;
import br.com.pinter.tqrespec.logging.Log;
import br.com.pinter.tqrespec.save.FileDataHolder;
import br.com.pinter.tqrespec.save.FileDataMap;
import br.com.pinter.tqrespec.save.FileWriter;
import br.com.pinter.tqrespec.save.Platform;
import br.com.pinter.tqrespec.save.stash.StashLoader;
import br.com.pinter.tqrespec.save.stash.StashWriter;
import br.com.pinter.tqrespec.tqdata.GameInfo;
import br.com.pinter.tqrespec.util.Constants;
import br.com.pinter.tqrespec.util.Util;
import com.google.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PlayerWriter extends FileWriter {
    private static final System.Logger logger = Log.getLogger(PlayerWriter.class.getName());
    @Inject
    private CurrentPlayerData saveData;

    @Inject
    private GameInfo gameInfo;

    @Override
    public int getCrcOffset() {
        return 0;
    }

    @Override
    public boolean isCreateCrc() {
        return false;
    }

    @Override
    protected FileDataHolder getSaveData() {
        return saveData;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean backupSaveGame(String fileName, String playerName) throws IOException {
        File backupDirectory = new File(gameInfo.getSavePath(), Constants.BACKUP_DIRECTORY);
        Path player = Paths.get(fileName);
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HH");
        String ts = df.format(new Date());
        boolean fullBackup = Settings.getAlwaysFullBackup();
        File destPlayerZip = new File(backupDirectory, String.format("%s%s_%s.zip", playerName, fullBackup ? "-fullbackup" : "", ts));

        //doesn't overwrite previous backup
        if (destPlayerZip.exists() && destPlayerZip.length() > 1) {
            return true;
        }

        if (!backupDirectory.exists() && !backupDirectory.mkdir()) {
            throw new IOException("Unable to create backup directory");
        }

        if (backupDirectory.canWrite()) {
            URI zipUri = URI.create("jar:" + destPlayerZip.toURI());
            HashMap<String, String> zipCreateOptions = new HashMap<>();
            zipCreateOptions.put("create", "true");

            try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, zipCreateOptions)) {
                final Path root = zipFs.getPath("/");
                if (fullBackup) {
                    Files.walkFileTree(player.getParent(), new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Path subPath = file.subpath(player.getParent().getNameCount() - 1, file.getNameCount());
                            final Path dest = zipFs.getPath(root.toString(), subPath.toString());
                            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            if (player.getParent().getNameCount() == dir.getNameCount()) {
                                return FileVisitResult.CONTINUE;
                            }
                            Path subPath = dir.subpath(player.getParent().getNameCount() - 1, dir.getNameCount());

                            final Path createDir = zipFs.getPath(root.toString(), subPath.toString());
                            Files.createDirectories(createDir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.createDirectories(zipFs.getPath(root.toString(), "/" + player.getName(player.getNameCount() - 2)));
                    Path destPlayer = zipFs.getPath("/" + player.getName(player.getNameCount() - 2) + "/" + player.getFileName());

                    Path srcDxb = Paths.get(saveData.getPlayerPath().toString(), "winsys.dxb");
                    Path destDxb = zipFs.getPath("/" + player.getName(player.getNameCount() - 2) + "/winsys.dxb");
                    Path srcDxg = Paths.get(saveData.getPlayerPath().toString(), "winsys.dxg");
                    Path destDxg = zipFs.getPath("/" + player.getName(player.getNameCount() - 2) + "/winsys.dxg");

                    Files.copy(player, destPlayer, StandardCopyOption.REPLACE_EXISTING);
                    copyFileTimes(player, destPlayer);

                    if (Files.exists(srcDxb)) {
                        Files.copy(srcDxb, destDxb, StandardCopyOption.REPLACE_EXISTING);
                        copyFileTimes(srcDxb, destDxb);
                    }

                    if (Files.exists(srcDxg)) {
                        Files.copy(srcDxg, destDxg, StandardCopyOption.REPLACE_EXISTING);
                        copyFileTimes(srcDxg, destDxg);
                    }
                }

                return true;
            } catch (IOException e) {
                logger.log(System.Logger.Level.ERROR, Constants.ERROR_MSG_EXCEPTION, e);
                return false;
            }
        }

        return false;
    }

    private void copyFileTimes(Path src, Path dst) throws IOException {
        Files.setAttribute(dst, "creationTime", Files.getAttribute(src, "creationTime"));
        Files.setAttribute(dst, "lastModifiedTime", Files.getAttribute(src, "lastModifiedTime"));
        Files.setAttribute(dst, "lastAccessTime", Files.getAttribute(src, "lastAccessTime"));
    }

    public boolean backupCurrent() throws IOException {
        String playerChr = saveData.getPlayerChr().toString();
        String playerName = saveData.getPlayerName();
        return this.backupSaveGame(playerChr, playerName);
    }

    public boolean save() {
        if (State.get().getSaveInProgress() != null && State.get().getSaveInProgress()) {
            return false;
        }
        State.get().setSaveInProgress(true);
        Path chrPath = saveData.getPlayerChr();
        String rootPath = chrPath.getRoot() + chrPath.subpath(0, chrPath.getNameCount() - 1).toString();
        String playerChr = chrPath.getFileName().toString();
        try {
            this.writeBuffer(rootPath, playerChr);
            State.get().setSaveInProgress(false);
            return true;
        } catch (IOException e) {
            State.get().setSaveInProgress(false);
            throw new UnhandledRuntimeException("Error saving character", e);
        }
    }

    public void copyCurrentSave(String toPlayerName) throws IOException {
        copyCurrentSave(toPlayerName, Platform.UNDEFINED, null);
    }

    public void copyCurrentSave(String toPlayerName, Platform conversionTarget, Path zipOutputPath) throws IOException {
        if(StringUtils.isBlank(toPlayerName)) {
            throw new IllegalArgumentException("character name can't be empty");
        }
        State.get().setSaveInProgress(true);

        try {
            String path = saveData.getPlayerPath().getParent().toString();
            String fromPlayerName = saveData.getPlayerName();

            Path playerSaveDirSource = Paths.get(path, "_" + fromPlayerName);
            Path playerSaveDirTarget = Paths.get(path, "_" + toPlayerName);
            String toZipPath = "/_" + toPlayerName;
            Platform oldPlatform = getSaveData().getDataMap().getPlatform();
            boolean backupOnly = false;

            if((conversionTarget.equals(Platform.UNDEFINED) && zipOutputPath == null && toPlayerName.equals(fromPlayerName))
                    || conversionTarget.equals(oldPlatform)) {
                State.get().setSaveInProgress(false);
                throw new IllegalStateException("An expected error occurred during character copy");
            }

            String saveId = RandomStringUtils.randomNumeric(10);
            if (zipOutputPath == null && Files.exists(playerSaveDirTarget)) {
                State.get().setSaveInProgress(false);
                throw new FileAlreadyExistsException("Target directory already exists: "+playerSaveDirTarget);
            }

            FileDataMap fileDataMap = (FileDataMap) saveData.getDataMap().deepClone();

            if(!toPlayerName.equals(saveData.getDataMap().getCharacterName())) {
                // set name before conversion
                fileDataMap.setString("myPlayerName", toPlayerName);
                if(fileDataMap.getPlatform().equals(Platform.MOBILE) && conversionTarget.equals(Platform.UNDEFINED)) {
                    //use new saveid
                    fileDataMap.setString("mySaveId", saveId);
                    toZipPath = "/__save"+saveId;
                }
            } else if(saveData.getPlatform().equals(Platform.MOBILE) && conversionTarget.equals(Platform.UNDEFINED)) {
                //use current saveid for directory name
                String currentSaveId = saveData.getDataMap().getString("mySaveId");
                toZipPath = "/__save"+currentSaveId;
            }

            if (!conversionTarget.equals(Platform.UNDEFINED)) {
                if(conversionTarget.equals(Platform.MOBILE)) {
                    toZipPath = "/__save" + saveId;
                }
                fileDataMap.convertTo(conversionTarget, saveId);
            }

            if (zipOutputPath != null) {
                if(toPlayerName.equals(fromPlayerName) && conversionTarget.equals(Platform.UNDEFINED)) {
                    backupOnly = true;
                }
                try (FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:" + zipOutputPath.toUri()), Map.of("create", "true"))) {
                    Path dir = zipfs.getPath(toZipPath);
                    String excludeCopyRegex = null;
                    if(conversionTarget.equals(Platform.MOBILE)) {
                        excludeCopyRegex = "(?i)(?:^backup.*|^winsys.dxg$|^winsys.dxb$|^settings.txt$)";
                    }
                    Util.copyDirectoryRecurse(playerSaveDirSource, dir, false, zipfs, excludeCopyRegex);
                    if(!backupOnly) {
                        Files.deleteIfExists(zipfs.getPath(dir.toString(), "Player.chr"));
                        writeBuffer(dir.toString(), "Player.chr", fileDataMap, zipfs);
                    }
                }
            } else {
                String excludeCopyRegex = "(?i)(?:^backup.*)";
                if(!conversionTarget.equals(Platform.UNDEFINED) && oldPlatform.equals(Platform.MOBILE)) {
                    excludeCopyRegex = "(?i)(?:^backup.*|^.winsys.dxg$|^.winsys.dxb$|^SavingChar.txt$)";
                }
                Util.copyDirectoryRecurse(playerSaveDirSource, playerSaveDirTarget, false, excludeCopyRegex);
                writeBuffer(playerSaveDirTarget.toString(), "Player.chr", fileDataMap);
                StashLoader stashLoader = new StashLoader();
                if (stashLoader.loadStash(playerSaveDirTarget, toPlayerName)) {
                    StashWriter stashWriter = new StashWriter(stashLoader.getSaveData());
                    stashWriter.save();
                }
            }
        } catch (IOException e) {
            logger.log(System.Logger.Level.ERROR, Constants.ERROR_MSG_EXCEPTION, e);
            throw new IOException(e);
        } finally {
            State.get().setSaveInProgress(false);
        }
    }
}
