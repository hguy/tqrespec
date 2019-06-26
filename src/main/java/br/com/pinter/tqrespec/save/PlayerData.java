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

package br.com.pinter.tqrespec.save;

import br.com.pinter.tqdatabase.Database;
import br.com.pinter.tqdatabase.models.Skill;
import br.com.pinter.tqrespec.Constants;
import br.com.pinter.tqrespec.Util;
import br.com.pinter.tqrespec.tqdata.Data;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("unused")
public class PlayerData {
    private static PlayerData instance = null;
    private String playerName = null;
    private Path playerChr = null;
    private ByteBuffer buffer = null;
    private ChangesTable changes = null;
    private Boolean saveInProgress = null;
    private boolean isCustomQuest = false;
    private final LinkedHashMap<String, PlayerSkill> playerSkills;

    private PlayerData() {
        playerSkills = new LinkedHashMap<>();
        changes = new ChangesTable();
    }

    public static PlayerData getInstance() {
        if (instance == null) {
            synchronized (PlayerData.class) {
                if (instance == null) {
                    instance = new PlayerData();
                }
            }
        }
        return instance;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public ChangesTable getChanges() {
        return changes;
    }

    public Path getPlayerChr() {
        return playerChr;
    }

    public void setPlayerChr(Path playerChr) {
        this.playerChr = playerChr;
    }

    public Boolean getSaveInProgress() {
        return saveInProgress;
    }

    public void setSaveInProgress(Boolean saveInProgress) {
        this.saveInProgress = saveInProgress;
    }

    public boolean isCustomQuest() {
        return isCustomQuest;
    }

    public void setCustomQuest(boolean customQuest) {
        isCustomQuest = customQuest;
    }

    void prepareSkillsList() {
        playerSkills.clear();
        for (String v : SaveData.getInstance().getVariableLocation().keySet()) {
            if (v.startsWith(Database.Variables.PREFIX_SKILL_NAME)) {
                for (int blockOffset : SaveData.getInstance().getVariableLocation().get(v)) {
                    BlockInfo b = SaveData.getInstance().getBlockInfo().get(blockOffset);
                    if (changes.get(b.getStart()) != null
                            && changes.get(b.getStart()).length == 0) {
                        //new block size is zero, was removed, ignore
                        continue;
                    }

                    PlayerSkill sb = new PlayerSkill();
                    sb.setSkillName((String) b.getVariables().get(Constants.Save.SKILL_NAME).getValue());
                    sb.setSkillEnabled((Integer) b.getVariables().get(Constants.Save.SKILL_ENABLED).getValue());
                    sb.setSkillActive((Integer) b.getVariables().get(Constants.Save.SKILL_ACTIVE).getValue());
                    sb.setSkillSubLevel((Integer) b.getVariables().get(Constants.Save.SKILL_SUB_LEVEL).getValue());
                    sb.setSkillTransition((Integer) b.getVariables().get(Constants.Save.SKILL_TRANSITION).getValue());
                    sb.setSkillLevel(changes.getInt(b.getStart(), Constants.Save.SKILL_LEVEL));
                    sb.setBlockStart(b.getStart());
                    if (sb.getSkillName() != null) {
                        synchronized (playerSkills) {
                            playerSkills.put(Objects.requireNonNull(Util.normalizeRecordPath(sb.getSkillName())),
                                    sb);
                        }
                    }
                }
            }
        }
    }

    public boolean isCharacterLoaded() {
        return getBuffer() != null;
    }

    public int getAvailableSkillPoints() {
        if (!isCharacterLoaded()) return 0;

        int block = SaveData.getInstance().getVariableLocation().get("skillPoints").get(0);
        BlockInfo statsBlock = SaveData.getInstance().getBlockInfo().get(block);
        return changes.getInt(statsBlock.getStart(), "skillPoints");
    }

    public LinkedHashMap<String, PlayerSkill> getPlayerSkills() {
        boolean update = false;

        for (PlayerSkill b : playerSkills.values()) {
            if (changes.get(b.getBlockStart()) != null
                    && changes.get(b.getBlockStart()).length == 0) {
                //new block size is zero, was removed, ignore
                update = true;
            }
        }

        if (playerSkills.isEmpty() || update) {
            prepareSkillsList();
        }

        return playerSkills;
    }

    public void reclaimSkillPoints(PlayerSkill sb) throws Exception {
        int blockStart = sb.getBlockStart();
        Skill skill = Data.db().getSkillDAO().getSkill(sb.getSkillName(), false);
        if (skill.isMastery()) {
            throw new IllegalStateException("Error reclaiming points. Mastery detected.");
        }

        BlockInfo skillToRemove = SaveData.getInstance().getBlockInfo().get(blockStart);
        VariableInfo varSkillLevel = skillToRemove.getVariables().get("skillLevel");
        if (varSkillLevel.getVariableType() == VariableInfo.VariableType.Integer) {
            int currentSkillPoints = changes.getInt("skillPoints");
            int currentSkillLevel = (int) varSkillLevel.getValue();
            changes.setInt("skillPoints", currentSkillPoints + currentSkillLevel);
            changes.removeBlock(blockStart);
            changes.setInt("max", changes.getInt("max") - 1);

            if (changes.get(blockStart) != null
                    && changes.get(blockStart).length == 0) {
                prepareSkillsList();
            }
        }
    }

    public void reclaimMasteryPoints(PlayerSkill sb) throws Exception {
        int blockStart = sb.getBlockStart();
        Skill mastery = Data.db().getSkillDAO().getSkill(sb.getSkillName(), false);
        if (!mastery.isMastery()) {
            throw new IllegalStateException("Error reclaiming points. Not a mastery.");
        }

        int currentSkillPoints = changes.getInt("skillPoints");
        int currentSkillLevel = changes.getInt(blockStart, "skillLevel");
        if(currentSkillLevel > 1) {
            changes.setInt("skillPoints", currentSkillPoints + (currentSkillLevel - 1));
            changes.setInt(blockStart, "skillLevel", 1);
            prepareSkillsList();
        }
    }

    public List<Skill> getPlayerMasteries() {
        List<Skill> ret = new ArrayList<>();
        for (PlayerSkill sb : getPlayerSkills().values()) {
            Skill skill = Data.db().getSkillDAO().getSkill(sb.getSkillName(), false);
            if (skill != null && skill.isMastery()) {
                ret.add(skill);
            }
        }
        return ret;
    }

    public List<Skill> getPlayerSkillsFromMastery(Skill mastery) {
        List<Skill> ret = new ArrayList<>();
        for (PlayerSkill sb : getPlayerSkills().values()) {
            Skill skill = Data.db().getSkillDAO().getSkill(sb.getSkillName(), false);
            if (skill != null && !skill.isMastery() && skill.getParentPath().equals(mastery.getRecordPath())) {
                ret.add(skill);
            }
        }
        return ret;
    }

    public void reset() {
        this.buffer = null;
        this.playerName = null;
        this.changes = new ChangesTable();
        this.playerChr = null;
        this.saveInProgress = null;
        SaveData.getInstance().reset();
    }


}
