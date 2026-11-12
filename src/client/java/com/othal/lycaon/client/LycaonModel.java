package com.othal.lycaon.client;

import com.othal.lycaon.entity.LycaonEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Full player model (with jacket/sleeve/pants overlay layers) plus a "carrying" arm pose. */
public class LycaonModel extends PlayerModel<LycaonEntity> {
    public LycaonModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(LycaonEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (entity.isCarrying()) {
            rightArm.xRot = -1.3f;
            leftArm.xRot = -1.3f;
            rightArm.yRot = -0.35f;
            leftArm.yRot = 0.35f;
            rightArm.zRot = 0.0f;
            leftArm.zRot = 0.0f;
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
        }
    }
}
