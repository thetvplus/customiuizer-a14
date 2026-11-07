package tv.withaibuild.customiuizer.mods.recents

import org.junit.Assert.*
import org.junit.Test

class DeckViewportTest {
    @Test fun landscapeUsesItsSafeContainerCentreAndUniformBoundedScale() {
        for(aspect in floatArrayOf(16f/9f,19.5f/9f,20f/9f)) for(density in floatArrayOf(1f,2.75f,4f)) {
            val height=400f*density; val width=height*aspect
            val safeLeft=40f*density; val usable=width-safeLeft-16f*density
            val cardWidth=width*.48f; val cardHeight=height*.56f
            for(percent in intArrayOf(0,55,75,85,999)) {
                val page=DeckViewport.pageScale(usable,height,cardWidth,cardHeight,percent)
                val model=IosDeckGeometry(usable,cardWidth,density,page)
                val top=DeckViewport.top(0f,0f,height,cardHeight,true)
                assertEquals(height*.5f,top+cardHeight*.5f,.01f)
                for(cursor in floatArrayOf(-.2f,0f,.2f)) {
                    val scale=model.scale(0,cursor)
                    assertTrue(cardHeight*scale<height)
                    assertTrue(scale.isFinite() && model.left(0,cursor,safeLeft).isFinite())
                }
                val normalized=model.scale(0,0f)*cardWidth/usable
                assertTrue(normalized<=.85f)
                assertEquals(safeLeft+(usable-cardWidth)*.5f,model.left(0,0f,safeLeft),.01f)
            }
        }
    }
    @Test fun automaticPortraitRetainsTheExistingPageScaleAndNativeTop() {
        assertEquals(.75f,DeckViewport.pageScale(400f,880f,220f,520f,0),0f)
        assertEquals(44f,DeckViewport.top(44f,30f,880f,520f,false),0f)
    }
}
