package io.github.eternaldesires.fireclay.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/** Walks a Terracotta Sentinel back to its post once it has strayed farther than {@code radius} (design §3.6). */
public class ReturnToPostGoal extends Goal {
	/** Close enough to the post to stop walking. */
	private static final double ARRIVED_SQR = 2.0 * 2.0;

	private final TerracottaSentinel sentinel;
	private final double radiusSqr;
	private final double speed;

	public ReturnToPostGoal(TerracottaSentinel sentinel, double radius, double speed) {
		this.sentinel = sentinel;
		this.radiusSqr = radius * radius;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos post = this.sentinel.getPost();
		return post != null && this.sentinel.getTarget() == null
				&& this.sentinel.blockPosition().distSqr(post) > this.radiusSqr;
	}

	@Override
	public boolean canContinueToUse() {
		BlockPos post = this.sentinel.getPost();
		return post != null && this.sentinel.getTarget() == null && !this.sentinel.getNavigation().isDone()
				&& this.sentinel.blockPosition().distSqr(post) > ARRIVED_SQR;
	}

	@Override
	public void start() {
		BlockPos post = this.sentinel.getPost();
		if (post != null) {
			this.sentinel.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, this.speed);
		}
	}

	@Override
	public void stop() {
		this.sentinel.getNavigation().stop();
	}
}
