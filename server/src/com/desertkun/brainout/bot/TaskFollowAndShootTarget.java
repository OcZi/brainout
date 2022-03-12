package com.desertkun.brainout.bot;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Queue;
import com.desertkun.brainout.Constants;
import com.desertkun.brainout.components.PlayerOwnerComponent;
import com.desertkun.brainout.components.WeaponSlotComponent;
import com.desertkun.brainout.content.bullet.Bullet;
import com.desertkun.brainout.content.bullet.LimitedBullet;
import com.desertkun.brainout.data.WayPointMap;
import com.desertkun.brainout.data.active.ActiveData;
import com.desertkun.brainout.data.components.BotControllerComponentData;
import com.desertkun.brainout.data.components.ServerTeamVisibilityComponentData;
import com.desertkun.brainout.data.components.ServerWeaponComponentData;
import com.desertkun.brainout.data.consumable.ConsumableRecord;
import com.desertkun.brainout.data.instrument.WeaponData;
import com.desertkun.brainout.utils.RandomValue;

public class TaskFollowAndShootTarget extends Task
{
    private final RandomValue fireTime;
    private final RandomValue firePauseTime;
    private float timer, lostSightTimer;
    private float fireTimer, firePauseTimer;
    private final ActiveData target;
    private BotControllerComponentData.VisibilityCheckOutput visibilityCheckOutput;

    public TaskFollowAndShootTarget(
        TaskStack stack,
        ActiveData target,
        RandomValue fireTime,
        RandomValue firePauseTime)
    {
        super(stack);

        this.target = target;
        this.fireTime = fireTime;
        this.firePauseTime = firePauseTime;
        this.visibilityCheckOutput = new BotControllerComponentData.VisibilityCheckOutput();
        this.fireTimer = 0;
        this.firePauseTimer = firePauseTime.getValue();
    }

    @Override
    protected void update(float dt)
    {
        timer -= dt;
        if (timer < 0)
        {
            timer = 0.1f;
            dt = 0.1f;

            // player's dead
            if (!target.isAlive())
            {
                getController().stopFollowing();
                setAim(false);
                openFire(false);
                pop();
                return;
            }

            float maximumDistance = 1.0f;

            if (checkWeapons(true))
                return;

            WeaponData currentWeapon = getCurrentWeapon();

            if (currentWeapon != null)
            {
                ServerWeaponComponentData sw = currentWeapon.getComponent(ServerWeaponComponentData.class);

                if (sw != null)
                {
                    ServerWeaponComponentData.Slot slot = sw.getSlot(Constants.Properties.SLOT_PRIMARY);

                    if (slot != null && slot.getBullet() != null)
                    {
                        Bullet bullet = slot.getBullet();

                        if (bullet instanceof LimitedBullet)
                        {
                            maximumDistance = ((LimitedBullet) bullet).getMaxDistance() * 0.75f;
                        }
                        else
                        {
                            maximumDistance = bullet.getPowerDistance() * 0.75f;
                        }
                    }
                }
            }

            // no more than 1 chunk away
            maximumDistance = Math.min(maximumDistance, 64);

            // can see the player right now
            if (getController().checkVisibility(target, maximumDistance, visibilityCheckOutput))
            {
                float targetAngle = visibilityCheckOutput.angle;

                setAim(true);

                this.lostSightTimer = 0;

                if (getController().isFollowingAnything())
                {
                    getController().stopFollowing();
                }

                float dst2 = Vector2.dst2(getPlayerData().getX(), getPlayerData().getY(), target.getX(), target.getY());

                float randomness;

                if (dst2 > 8 * 8)
                {
                    randomness = 4.0f;
                }
                else
                {
                    randomness = 8f;
                }

                if (getController().lerpAngle(targetAngle + MathUtils.random(-randomness, randomness)))
                {
                    fireTimer -= dt;
                    if (fireTimer <= 0)
                    {
                        if (fireTimer < -firePauseTimer)
                        {
                            resetTimers();

                            openFire(true);
                        }
                        else
                        {
                            openFire(false);
                        }
                    }
                    else
                    {
                        openFire(true);
                    }
                }
                else
                {
                    openFire(false);
                }
            }
            else
            {
                setAim(false);
                openFire(false);

                if (!getController().isFollowing(target))
                {
                    getController().follow(target, null, this::stuck, this::gotBlocksInOurWay);
                }

                lostSightTimer += dt;
                if (lostSightTimer > 4.0f)
                {
                    pop();
                }
            }
        }
    }

    private void gotBlocksInOurWay(Queue<WayPointMap.BlockCoordinates> blocks)
    {
        if (getStack().getTasks().size > 0 &&
            getStack().getTasks().last() instanceof TaskDestroyBlocks)
            return;

        pushTask(new TaskDestroyBlocks(getStack(), blocks));
    }

    protected void stuck()
    {

    }

    private void resetTimers()
    {
        this.fireTimer = fireTime.getValue();
        this.firePauseTimer = firePauseTime.getValue();
    }
}