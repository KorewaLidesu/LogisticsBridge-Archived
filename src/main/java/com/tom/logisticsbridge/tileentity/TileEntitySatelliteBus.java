package com.tom.logisticsbridge.tileentity;

import com.raoulvdberge.refinedstorage.tile.TileNode;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.node.NetworkNodeSatellite;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import network.rs485.logisticspipes.SatellitePipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TileEntitySatelliteBus extends TileNode<NetworkNodeSatellite> implements IIdPipe, SatellitePipe {

    @Override
    public NetworkNodeSatellite createNode(World world, BlockPos pos) {
        return new NetworkNodeSatellite(world, pos);
    }

    @Override
    public String getNodeId() {
        return NetworkNodeSatellite.ID;
    }

    @Override
    public String getPipeID(int fid) {
        return resultPipeName;
    }

    @Override
    public void setPipeID(int fid, String integer, EntityPlayer player) {
        if (player == null) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(integer).setId(fid).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToServer(packet);
        } else if (MainProxy.isServer(player.world)) {
            final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(integer).setId(fid).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
            MainProxy.sendPacketToPlayer(packet, player);
        }
        this.resultPipeName = integer;
        ensureAllSatelliteStatus();
    }

    @Override
    public String getName(int id) {
        return "gui.satelliteBus.id";
    }
    
    public String getSatelliteBusName() {
        return resultPipeName;
    }

    public void openGui(EntityPlayer player, EnumHand hand) {
        if (player.getHeldItem(hand).getItem() == LogisticsBridge.packageItem) {
            ItemStack is = player.getHeldItem(hand);
            if (!is.hasTagCompound()) is.setTagCompound(new NBTTagCompound());
            is.getTagCompound().setString("__pkgDest", getNode().satelliteId);
            player.inventoryContainer.detectAndSendChanges();
        } else {
            player.openGui(LogisticsBridge.modInstance, 5, world, pos.getX(), pos.getY(), pos.getZ());
            ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(getNode().satelliteId).setId(0).setPosX(pos.getX()).setPosY(pos.getY()).setPosZ(pos.getZ());
            MainProxy.sendPacketToPlayer(packet, player);
        }
    }

}
