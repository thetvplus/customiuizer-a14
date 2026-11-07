package tv.withaibuild.customiuizer.mods.recents

import org.junit.Assert.*
import org.junit.Test

class DeckSwipeInputTest {
    @Test fun unconsumedFailedAndNestedEventsCannotLeakIntoLaterMoves() {
        val input=DeckSwipeInput(); val helper=Any(); val vertical=Any()
        input.calculate(vertical,-10f); assertTrue(input.consume(helper).isNaN())
        input.begin(helper,vertical); input.calculate(vertical,-20f); input.calculate(vertical,-30f)
        assertEquals(-30f,input.consume(helper),0f); assertTrue(input.consume(helper).isNaN()); input.end()
        input.begin(helper,vertical); input.calculate(vertical,-40f); input.end() // native failure / no callback
        input.begin(helper,vertical); assertTrue(input.consume(helper).isNaN())
        input.calculate(Any(),-50f); assertTrue(input.consume(helper).isNaN())
        input.calculate(vertical,-60f); assertTrue(input.consume(Any()).isNaN())
        input.begin(helper,vertical); input.calculate(vertical,-70f); input.end()
        assertTrue(input.consume(helper).isNaN()); input.end()
        input.begin(helper,vertical); input.calculate(vertical,-80f)
        assertEquals(-80f,input.consume(helper),0f); input.end()
    }
}
