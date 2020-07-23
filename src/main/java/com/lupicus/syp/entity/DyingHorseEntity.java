package com.lupicus.syp.entity;

import java.util.UUID;

import com.lupicus.syp.Main;
import com.lupicus.syp.advancements.ModTriggers;
import com.lupicus.syp.config.MyConfig;
import com.lupicus.syp.item.ModItems;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public abstract class DyingHorseEntity extends AbstractHorseEntity implements IDying
{
	protected long woundedTime;

	protected DyingHorseEntity(EntityType<? extends AbstractHorseEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Override
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
		if (compound.contains("WoundedTime"))
		{
			woundedTime = compound.getLong("WoundedTime");
			dataManager.set(POSE, Pose.DYING);
			setHealth(0.0F);
			deathTime = 20;
		}
	}

	@Override
	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		if (isDying())
			compound.putLong("WoundedTime", woundedTime);
	}

	@Override
	public void onDeath(DamageSource cause)
	{
		if (!isTame())
		{
			super.onDeath(cause);
			return;
		}
		if (!isDying())
		{
			PlayerEntity player = null;
			UUID uuid = getOwnerUniqueId();
			if (uuid != null)
				player = world.getPlayerByUuid(uuid);
			if (player != null && !world.isRemote && world.getGameRules().getBoolean(GameRules.SHOW_DEATH_MESSAGES)
					&& player instanceof ServerPlayerEntity) {
				ITextComponent msg = new TranslationTextComponent(Main.MODID + ".pet_dying.horse");
				if (hasCustomName())
					msg.appendSibling(new StringTextComponent(" ")).appendSibling(getCustomName());
				if (MyConfig.showLoc)
					msg.appendSibling(new StringTextComponent(" " + formatLoc(getPositionVec())));
				player.sendMessage(msg);
			}
			detach();
			this.dataManager.set(POSE, Pose.DYING);
			woundedTime = world.getGameTime();
		}
	}

	@Override
	protected void onDeathUpdate()
	{
		if (!isTame())
		{
			super.onDeathUpdate();
			return;
		}
		if (deathTime < 20)
			deathTime++;
		else if (!world.isRemote && world.getGameTime() - woundedTime >= MyConfig.deathTimer)
		{
			if (MyConfig.autoHeal)
			{
				cureEntity(ModItems.GOLDEN_PET_BANDAGE);
				world.setEntityState(this, (byte) 101);
			}
			else
			{
				super.onDeath(DamageSource.GENERIC);
				remove();
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void handleStatusUpdate(byte id)
	{
		if (id == 101)
			cureEntity(ModItems.GOLDEN_PET_BANDAGE);
		else {
			super.handleStatusUpdate(id);
			if (id == 3 && isTame())
			{
				deathTime = 19;
				super.onDeathUpdate();
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean isDying()
	{
		return dataManager.get(POSE) == Pose.DYING && isTame() && !removed;
	}

	@Override
	public boolean dyingInteract(PlayerEntity player, Hand hand)
	{
		if (!isDying())
			return false;
		ItemStack itemstack = player.getHeldItem(hand);
		Item item = itemstack.getItem();
		if (item == ModItems.PET_BANDAGE || item == ModItems.GOLDEN_PET_BANDAGE)
		{
            if (!player.abilities.isCreativeMode) {
            	itemstack.shrink(1);
            }
			if (player instanceof ServerPlayerEntity) {
				ModTriggers.SAVE_PET.trigger((ServerPlayerEntity) player, this);
			}
			cureEntity(item);
			return true;
		}
		else if (player.func_226563_dT_()) {
			openGUI(player);
			return true;
		}
		return false;
	}

	void cureEntity(Item item)
	{
		for (int i = 0; i < 10; ++i) {
			double d0 = this.rand.nextGaussian() * 0.02D;
			double d1 = this.rand.nextGaussian() * 0.02D;
			double d2 = this.rand.nextGaussian() * 0.02D;
			this.world.addParticle(ParticleTypes.HEART, this.getPosXRandom(1.0D), this.getPosYRandom(),
					this.getPosZRandom(1.0D), d0, d1, d2);
		}
		setMotion(Vec3d.ZERO);
		this.dataManager.set(POSE, Pose.STANDING);
		setHealth(1.0F);
		deathTime = 0;
		if (item == ModItems.GOLDEN_PET_BANDAGE)
			addPotionEffect(new EffectInstance(Effects.REGENERATION, MyConfig.healTime, 1));
	}

	@Override
	protected boolean isMovementBlocked() {
		if (isDying())
			return true;
		return super.isMovementBlocked();
	}
}