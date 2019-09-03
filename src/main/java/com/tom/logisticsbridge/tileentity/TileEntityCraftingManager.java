package com.tom.logisticsbridge.tileentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.IInventoryChangedListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.item.VirtualPattern;
import com.tom.logisticsbridge.item.VirtualPattern.VirtualPatternHandler;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;

import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class TileEntityCraftingManager extends AENetworkInvTile implements ITickable, ICraftingProvider, IIdPipe, IInventoryChangedListener {
	private static final IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
	private int priority;
	private List<ICraftingPatternDetails> craftingList = null;
	public String supplyID = "";
	public InventoryBasic inv = new InventoryBasic("", false, 27);
	public InvWrapper wr = new InvWrapper(inv);
	private IActionSource as = new MachineSource(this);
	private List<ItemStack> toInsert = new ArrayList<>();
	public TileEntityCraftingManager() {
		getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
		inv.addInventoryChangeListener(this);
	}

	@Override
	public AECableType getCableConnectionType(AEPartLocation aePartLocation) {
		return AECableType.SMART;
	}

	@Override
	public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
		if(patternDetails instanceof VirtualPatternHandler){
			if(toInsert.size() > 10)return false;
			ItemStack stack = patternDetails.getCondensedOutputs()[0].asItemStackRepresentation();
			if(!stack.hasTagCompound())return false;
			String id = stack.getTagCompound().getString("__pkgDest");
			toInsert.add(LogisticsBridge.packageStack(patternDetails.getCondensedInputs()[0].asItemStackRepresentation(), 1, id, true));
			return true;
		}
		if(supplyID.isEmpty())return false;
		PartSatelliteBus bus = find(supplyID);
		if(bus == null)return false;
		for (int i = 0;i < table.getSizeInventory();i++) {
			ItemStack is = table.getStackInSlot(i);
			if(!is.isEmpty() && is.getItem() == LogisticsBridge.packageItem && is.hasTagCompound()){
				ItemStack pkgItem = new ItemStack(is.getTagCompound());
				String id = is.getTagCompound().getString("__pkgDest");
				PartSatelliteBus b = find(id);
				if(b == null)return false;
				pkgItem.setCount(pkgItem.getCount() * is.getCount());
				if(!b.push(pkgItem))return false;
				table.removeStackFromSlot(i);
			}
		}
		return bus.push(table);
	}
	public ItemStack insertItem(ItemStack stack, boolean simulate) {
		if(this.getProxy().getNode() == null || stack.isEmpty())return stack;
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IAEItemStack st = ITEMS.createStack(stack);
		IAEItemStack r = i.injectItems(st, simulate ? Actionable.SIMULATE : Actionable.MODULATE, as);
		return r == null ? ItemStack.EMPTY : r.createItemStack();
	}
	private PartSatelliteBus find(String id){
		for (final IGridNode node : getNode().getGrid().getMachines(PartSatelliteBus.class)) {
			IGridHost h = node.getMachine();
			if(h instanceof PartSatelliteBus){
				PartSatelliteBus satellite = (PartSatelliteBus) h;
				if(satellite.satelliteId.equals(id)){
					return satellite;
				}
			}
		}
		return null;
	}
	@Override
	public boolean isBusy() {
		return supplyID.isEmpty();
	}

	@Override
	public void provideCrafting(ICraftingProviderHelper craftingTracker) {
		if( getNode() != null && this.getNode().isActive() && this.craftingList != null )
		{
			for( final ICraftingPatternDetails details : this.craftingList )
			{
				details.setPriority( this.priority );
				craftingTracker.addCraftingOption( this, details );
			}
		}
	}

	/*@Override
	public void setNextId(EntityPlayer player, int id) {
		if (MainProxy.isClient(player.world)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(SetIDPacket.class).setInc(true).setId(0).setTilePos(this);
			MainProxy.sendPacketToServer(packet);
		} else {
			supplyID = getNextConnectSatelliteId(false);
			final CoordinatesPacket packet = PacketHandler.getPacket(ResultPipeID.class).setPipeID(supplyID).setId(0).setTilePos(this);
			MainProxy.sendPacketToPlayer(packet, player);
		}
	}

	@Override
	public void setPrevId(EntityPlayer player, int id) {
		if (MainProxy.isClient(player.world)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(SetIDPacket.class).setInc(false).setId(0).setTilePos(this);
			MainProxy.sendPacketToServer(packet);
		} else {
			supplyID = getNextConnectSatelliteId(true);
			final CoordinatesPacket packet = PacketHandler.getPacket(ResultPipeID.class).setPipeID(supplyID).setId(0).setTilePos(this);
			MainProxy.sendPacketToPlayer(packet, player);
		}
	}*/
	/*protected int getNextConnectSatelliteId(boolean prev) {
		int closestIdFound = prev ? 0 : Integer.MAX_VALUE;
		for (final IGridNode node : getNode().getGrid().getMachines(PartSatelliteBus.class)) {
			IGridHost h = node.getMachine();
			if(h instanceof PartSatelliteBus){
				PartSatelliteBus satellite = (PartSatelliteBus) h;
				if (!prev && satellite.satelliteId > supplyID && satellite.satelliteId < closestIdFound) {
					closestIdFound = satellite.satelliteId;
				} else if (prev && satellite.satelliteId < supplyID && satellite.satelliteId > closestIdFound) {
					closestIdFound = satellite.satelliteId;
				}
			}
		}
		if (closestIdFound == Integer.MAX_VALUE) {
			return supplyID;
		}
		return closestIdFound;
	}*/
	@Override
	public String getPipeID(int id) {
		return supplyID;
	}

	@Override
	public void setPipeID(int id, String pipeID, EntityPlayer player) {
		if(pipeID == null)pipeID = "";
		if (player == null) {
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setTilePos(getTile());
			MainProxy.sendPacketToServer(packet);
		} else if (MainProxy.isServer(player.world)){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setTilePos(getTile());
			MainProxy.sendPacketToPlayer(packet, player);
		}
		supplyID = pipeID;
	}
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound.setString("supplyName", supplyID);
		return super.writeToNBT(compound);
	}
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		supplyID = compound.getString("supplyName");
		super.readFromNBT(compound);
		updateCraftingList();
	}
	private void addToCraftingList( final ItemStack is )
	{
		if( is.isEmpty() )
		{
			return;
		}

		if( is.getItem() instanceof ICraftingPatternItem )
		{
			final ICraftingPatternItem cpi = (ICraftingPatternItem) is.getItem();
			final ICraftingPatternDetails details = cpi.getPatternForItem( is, world );

			if( details != null )
			{
				if( this.craftingList == null )
				{
					this.craftingList = new ArrayList<>();
				}

				IAEItemStack[] cin = details.getCondensedInputs();
				IAEItemStack[] in = details.getInputs();

				List<ItemStack> pkgs = new ArrayList<>();
				visitArray(cin, pkgs, false);
				visitArray(in, pkgs, false);
				pkgs.stream().map(p -> VirtualPattern.create(new ItemStack(p.getTagCompound()), p)).forEach(craftingList::add);

				this.craftingList.add( details );
			}
		}
	}
	private void visitArray(IAEItemStack[] array, List<ItemStack> pkgs, boolean act) {
		for (int i = 0;i < array.length;i++) {
			IAEItemStack iaeItemStack = array[i];
			if(iaeItemStack != null){
				ItemStack is = iaeItemStack.getDefinition();
				if(is.getItem() == LogisticsBridge.packageItem){
					if(is.hasTagCompound() && is.getTagCompound().getBoolean("__actStack") == act){
						if(!act){
							is = is.copy();
							is.getTagCompound().setBoolean("__actStack", true);
							array[i] = ITEMS.createStack(is);
						}
						pkgs.add(is);
					}
				}
			}
		}
	}

	private void updateCraftingList()
	{
		final Boolean[] accountedFor = new Boolean[inv.getSizeInventory()]; // 9...
		Arrays.fill(accountedFor, false);

		assert ( accountedFor.length == this.inv.getSizeInventory() );

		if( !this.getProxy().isReady() )
		{
			return;
		}

		if( this.craftingList != null )
		{
			Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
			while( i.hasNext() )
			{
				final ICraftingPatternDetails details = i.next();
				boolean found = false;

				for( int x = 0; x < accountedFor.length; x++ )
				{
					final ItemStack is = this.inv.getStackInSlot( x );
					if( details.getPattern() == is )
					{
						accountedFor[x] = found = true;
					}
				}

				if(details instanceof VirtualPatternHandler){
					found = true;
				}

				if( !found )
				{
					i.remove();
				}
			}
			List<ItemStack> pkgs = new ArrayList<>();
			for (ICraftingPatternDetails is : craftingList) {
				visitArray(is.getCondensedInputs(), pkgs, true);
				visitArray(is.getInputs(), pkgs, true);
			}
			i = this.craftingList.iterator();
			while( i.hasNext() )
			{
				final ICraftingPatternDetails details = i.next();
				boolean found = false;

				if(details instanceof VirtualPatternHandler){
					if(!pkgs.isEmpty()){
						IAEItemStack[] in = details.getCondensedOutputs();
						for (int j = 0;j < in.length;j++) {
							IAEItemStack iaeItemStack = in[j];
							ItemStack is = iaeItemStack == null ? ItemStack.EMPTY : iaeItemStack.asItemStackRepresentation();
							if(!is.isEmpty() && is.getItem() == LogisticsBridge.packageItem && pkgs.stream().anyMatch(s -> ItemStack.areItemStackTagsEqual(s, is))){
								found = true;
								break;
							}
						}
					}
				}else found = true;

				if( !found )
				{
					i.remove();
				}
			}
		}

		for( int x = 0; x < accountedFor.length; x++ )
		{
			if( !accountedFor[x] )
			{
				this.addToCraftingList( this.inv.getStackInSlot( x ) );
			}
		}

		this.getNode().getGrid().postEvent( new MENetworkCraftingPatternChange( this, this.getNode() ) );
	}
	@Override
	public void onInventoryChanged(IInventory invBasic) {
		updateCraftingList();
	}
	@Override
	public String getName(int id) {
		return null;
	}
	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}
	@Override
	public IItemHandler getInternalInventory() {
		return wr;
	}
	@Override
	public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
	}
	@Override
	public NBTTagCompound downloadSettings(SettingsFrom from) {
		NBTTagCompound tag = super.downloadSettings(from);
		if(tag == null)tag = new NBTTagCompound();
		if(from == SettingsFrom.DISMANTLE_ITEM){
			tag.setTag("patterns", LogisticsBridge.saveAllItems(inv));
			inv.clear();
		}
		if(!supplyID.isEmpty())tag.setString("satName", supplyID);
		return tag.hasNoTags() ? null : tag;
	}
	@Override
	public void uploadSettings(SettingsFrom from, NBTTagCompound compound) {
		super.uploadSettings(from, compound);
		if(from == SettingsFrom.DISMANTLE_ITEM){
			LogisticsBridge.loadAllItems(compound.getTagList("patterns", 10), inv);
		}
		supplyID = compound.getString("satName");
	}
	private IGridNode getNode(){
		return this.getProxy().getNode();
	}
	@Override
	public void onReady()
	{
		super.onReady();
		updateCraftingList();
	}

	@Override
	public void update() {
		if(!world.isRemote){
			if(!toInsert.isEmpty()){
				ListIterator<ItemStack> itr = toInsert.listIterator();
				while (itr.hasNext()) {
					ItemStack stack = itr.next();
					if(stack.isEmpty()){
						itr.remove();
						continue;
					}
					ItemStack result = insertItem(stack, false);
					if(!result.isEmpty())itr.set(result);
					else itr.remove();
				}
			}
		}
	}
	@Override
	public List<String> list(int id) {
		List<String> ret = new ArrayList<>();
		for (final IGridNode node : getNode().getGrid().getMachines(PartSatelliteBus.class)) {
			IGridHost h = node.getMachine();
			if(h instanceof PartSatelliteBus){
				PartSatelliteBus satellite = (PartSatelliteBus) h;
				ret.add(satellite.satelliteId);
			}
		}

		return ret;
	}
	public void openGui(EntityPlayer playerIn){
		playerIn.openGui(LogisticsBridge.modInstance, GuiIDs.CraftingManager.ordinal(), world, pos.getX(), pos.getY(), pos.getZ());
		ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(supplyID).setId(0).setPosX(pos.getX()).setPosY(pos.getY()).setPosZ(pos.getZ());
		MainProxy.sendPacketToPlayer(packet, playerIn);
	}
}
