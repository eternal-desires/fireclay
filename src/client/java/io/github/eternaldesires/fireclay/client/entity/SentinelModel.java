package io.github.eternaldesires.fireclay.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import io.github.eternaldesires.fireclay.Fireclay;

/**
 * The Sentinel's hand-built humanoid model; geometry is contract §3. Animation is procedural: walk swing,
 * head look, an iron-golem style two-armed attack, a crest that sways with the stride, and the awakening pose
 * (bowed, arms crossed at the chest) that blends back to idle as the sequence ends.
 */
public class SentinelModel extends EntityModel<SentinelRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Fireclay.id("terracotta_sentinel"), "main");
	private static final String CREST = "crest";
	private static final String WAIST = "waist";

	private final ModelPart head;
	private final ModelPart crest;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public SentinelModel(ModelPart root) {
		super(root);
		this.head = root.getChild(PartNames.HEAD);
		this.crest = this.head.getChild(CREST);
		this.body = root.getChild(PartNames.BODY);
		this.rightArm = root.getChild(PartNames.RIGHT_ARM);
		this.leftArm = root.getChild(PartNames.LEFT_ARM);
		this.rightLeg = root.getChild(PartNames.RIGHT_LEG);
		this.leftLeg = root.getChild(PartNames.LEFT_LEG);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild(PartNames.HEAD,
				CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
		head.addOrReplaceChild(CREST,
				CubeListBuilder.create().texOffs(32, 0).addBox(-1, -11, -3, 2, 3, 5), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild(PartNames.BODY,
				CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4), PartPose.ZERO);
		body.addOrReplaceChild(WAIST,
				CubeListBuilder.create().texOffs(0, 32).addBox(-4.5f, 10, -2.5f, 9, 3, 5), PartPose.ZERO);
		root.addOrReplaceChild(PartNames.RIGHT_ARM,
				CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
		root.addOrReplaceChild(PartNames.LEFT_ARM,
				CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		root.addOrReplaceChild(PartNames.RIGHT_LEG,
				CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-1.9f, 12, 0));
		root.addOrReplaceChild(PartNames.LEFT_LEG,
				CubeListBuilder.create().texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(1.9f, 12, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(SentinelRenderState state) {
		super.setupAnim(state);

		// Head look; render-state yRot/xRot are degrees.
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;

		// Walk: legs and opposite arms swing; the heavy arms swing at half amplitude.
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float stride = Mth.cos(pos * 0.6662f) * 1.4f * speed;
		this.rightLeg.xRot = stride;
		this.leftLeg.xRot = -stride;
		this.rightArm.xRot = -stride * 0.5f;
		this.leftArm.xRot = stride * 0.5f;

		// Crest sways a little with the stride and with idle breathing.
		this.crest.zRot = Mth.sin(pos * 0.6662f) * 0.08f * speed + Mth.sin(state.ageInTicks * 0.05f) * 0.02f;

		// Attack: both arms come down together, like an iron golem.
		if (state.attackAnim > 0.0f) {
			float swing = -2.0f + 1.5f * Mth.triangleWave(state.attackAnim, 10.0f);
			this.rightArm.xRot = swing;
			this.leftArm.xRot = swing;
		}

		// Awakening: p = 1 at the start of the sequence, 0 once awake.
		float p = state.awaken;
		if (p > 0.0f) {
			this.body.xRot = 0.6f * p;
			this.head.xRot += 0.5f * p;
			this.crest.xRot = -0.3f * p;
			// Bowed like a vanilla crouch: body and head lowered, legs pushed back under the hips.
			this.body.y += 3.2f * p;
			this.head.y += 4.2f * p;
			this.rightArm.y += 3.2f * p;
			this.leftArm.y += 3.2f * p;
			this.rightLeg.z += 4.0f * p;
			this.leftLeg.z += 4.0f * p;
			// Arms crossed at the chest, blending back to the idle pose.
			this.rightArm.xRot = Mth.lerp(p, this.rightArm.xRot, -1.3f);
			this.rightArm.yRot = -0.6f * p;
			this.leftArm.xRot = Mth.lerp(p, this.leftArm.xRot, -1.3f);
			this.leftArm.yRot = 0.6f * p;
		}
	}
}
