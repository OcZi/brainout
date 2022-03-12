package com.desertkun.brainout.bot.freeplay;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Queue;
import com.desertkun.brainout.BrainOutServer;
import com.desertkun.brainout.Constants;
import com.desertkun.brainout.bot.*;
import com.desertkun.brainout.common.msg.server.CustomPlayerAnimationMsg;
import com.desertkun.brainout.data.WayPointMap;
import com.desertkun.brainout.data.active.ActiveData;
import com.desertkun.brainout.data.active.PlayerData;
import com.desertkun.brainout.mode.GameMode;
import com.desertkun.brainout.reflection.Reflect;
import com.desertkun.brainout.reflection.ReflectAlias;
import com.desertkun.brainout.utils.RandomValue;

@Reflect("bot.freeplay.TaskHoldStillNotice")
public class TaskHoldStillNotice extends Task
{
    private Vector2 tmp;
    private float timer, complete;
    private boolean reachedSubPoint;
    private float changeAngleCounter;

    public TaskHoldStillNotice(TaskStack stack)
    {
        super(stack);

        this.tmp = new Vector2();
    }

    @Override
    protected void update(float dt)
    {
        timer -= dt;

        if (timer > 0)
            return;

        timer = 0.3f;

        if (checkWeapons(false))
            return;

        checkEnemy();
    }

    @Override
    public void gotShotFrom(ActiveData shooter)
    {
        attack(shooter);
    }

    private void gotBlocksInOurWay(Queue<WayPointMap.BlockCoordinates> blocks)
    {
        if (getStack().getTasks().size > 0 &&
            getStack().getTasks().last() instanceof TaskDestroyBlocks)
            return;

        pushTask(new TaskDestroyBlocks(getStack(), blocks));
    }

    private boolean checkEnemy()
    {
        GameMode gameMode = BrainOutServer.Controller.getGameMode();

        float maxDistance = 64f;

        Array<ActiveData> a = getMap().getActivesForTag(Constants.ActiveTags.PLAYERS,
            activeData ->
        {
            if (activeData == getPlayerData())
                return false;

            if (!(activeData instanceof PlayerData))
                return false;

            if (activeData.getOwnerId() >= 0 && getPlayerData().getOwnerId() >= 0)
            {
                if (!gameMode.isEnemies(activeData.getOwnerId(), getPlayerData().getOwnerId()))
                    return false;
            }
            else
            {
                if (!gameMode.isEnemiesActive(activeData, getPlayerData()))
                    return false;
            }

            return getController().checkVisibility(activeData, maxDistance, null);
        });

        if (a.size > 0)
        {
            BrainOutServer.Controller.getClients().sendTCP(new CustomPlayerAnimationMsg(
                getPlayerData(), "notice", null));

            attack(a.random());

            pushTask(new TaskWait(getStack(), 1.2f));

            return true;
        }

        return false;
    }

    private void attack(ActiveData a)
    {
        if (haveKnife())
        {
            pushTask(new TaskFollowAndShootTarget(getStack(), a,
                    new RandomValue(0.0f, 0.1f), new RandomValue(0.0f, 0.1f)));
        }
        else
        {
            pushTask(new TaskFollowAndShootTarget(getStack(), a,
                    new RandomValue(0.5f, 1.0f), new RandomValue(0.6f, 1.3f)));
        }
    }
}