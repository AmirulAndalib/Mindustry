package mindustry.editor;

import arc.struct.*;
import mindustry.annotations.Annotations.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

import static mindustry.Vars.*;

public class DrawOperation{
    private LongSeq array = new LongSeq();

    public boolean isEmpty(){
        return array.isEmpty();
    }

    public void addOperation(long op){
        array.add(op);
    }

    public void undo(){
        for(int i = array.size - 1; i >= 0; i--){
            updateTile(i);
        }
    }

    public void redo(){
        for(int i = 0; i < array.size; i++){
            updateTile(i);
        }
    }

    private void updateTile(int i){
        long l = array.get(i);
        array.set(i, TileOp.get(TileOp.x(l), TileOp.y(l), TileOp.type(l), getTile(editor.tile(TileOp.x(l), TileOp.y(l)), TileOp.type(l))));
        setTile(editor.tile(TileOp.x(l), TileOp.y(l)), TileOp.type(l), TileOp.value(l));
    }

    short getTile(Tile tile, byte type){
        if(type == OpType.floor.ordinal()){
            return tile.floorID();
        }else if(type == OpType.block.ordinal()){
            return tile.blockID();
        }else if(type == OpType.rotation.ordinal()){
            return tile.build == null ? 0 : (byte)tile.build.rotation;
        }else if(type == OpType.team.ordinal()){
            return (byte)tile.getTeamID();
        }else if(type == OpType.overlay.ordinal()){
            return tile.overlayID();
        }
        throw new IllegalArgumentException("Invalid type.");
    }

    void setTile(Tile tile, byte type, short to){
        editor.load(() -> {
            if(type == OpType.floor.ordinal()){
                if(content.block(to) instanceof Floor floor){
                    tile.setFloor(floor);
                }
            }else if(type == OpType.block.ordinal()){
                tile.getLinkedTiles(t -> editor.renderer.updatePoint(t.x, t.y));

                Block block = content.block(to);
                tile.setBlock(block, tile.team(), tile.build == null ? 0 : tile.build.rotation);
                if(tile.build != null){
                    tile.build.enabled = true;
                }

                tile.getLinkedTiles(t -> editor.renderer.updatePoint(t.x, t.y));
            }else if(type == OpType.rotation.ordinal()){
                if(tile.build != null) tile.build.rotation = to;
            }else if(type == OpType.team.ordinal()){
                tile.setTeam(Team.get(to));
            }else if(type == OpType.overlay.ordinal()){
                tile.setOverlayID(to);
            }
        });
        editor.renderer.updatePoint(tile.x, tile.y);
    }

    @Struct
    class TileOpStruct{
        short x;
        short y;
        byte type;
        short value;
    }

    public enum OpType{
        floor,
        block,
        rotation,
        team,
        overlay
    }
}
