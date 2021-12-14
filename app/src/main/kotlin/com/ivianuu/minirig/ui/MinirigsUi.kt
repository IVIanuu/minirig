package com.ivianuu.minirig.ui

import androidx.compose.foundation.*
import androidx.compose.material.*
import androidx.compose.ui.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.resource.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.domain.*

@Provide
object MinirigsKey : RootKey

@Provide
val minirigsUi = ModelKeyUi<MinirigsKey, MinirigsModel> {
  Scaffold(
    topBar = { TopAppBar(title = { Text("Minirigs") }) }
  ) {
    ResourceVerticalListFor(model.minirigs) { minirig ->
      ListItem(
        title = { Text(minirig.name) },
        modifier = Modifier.clickable { model.openMinirig(minirig) }
      )
    }
  }
}

data class MinirigsModel(
  val minirigs: Resource<List<Minirig>>,
  val openMinirig: (Minirig) -> Unit
)

@Provide
fun minirigsModel(
  minirigRepository: MinirigRepository,
  navigator: Navigator,
  SS: StateScope
) = MinirigsModel(
  minirigs = minirigRepository.minirigs.bindResource(),
  openMinirig = action { minirig -> navigator.push(MinirigKey(minirig.address)) }
)
