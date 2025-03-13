package dev.vinigouveia.moviesapp.home

import dev.vinigouveia.moviesapp.R

data class HomeUiModel(
    val coverImageResourceId: Int = R.drawable.tears_of_steal_cover,
    val streamUrl: String = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
    val attribution: String = "(CC) Blender foundation | mango.blender.org",
    val description: String = "The film’s premise is about a group of warriors and scientists, who gathered at the “Oude Kerk” in Amsterdam to stage a crucial event from the past, in a desperate attempt to rescue the world from destructive robots.",
    val adTagURl: String? = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=300x250&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpostpod&cmsid=496&vid=short_onecue&correlator="
)
