package com.hearopilot.app.ui.screens

import androidx.annotation.StringRes
import com.hearopilot.app.ui.R

enum class PhraseCategory(@StringRes val labelRes: Int) {
    GREETINGS(R.string.phrase_category_greetings),
    TRANSPORT(R.string.phrase_category_transport),
    ACCOMMODATION(R.string.phrase_category_accommodation),
    FOOD(R.string.phrase_category_food),
    SHOPPING(R.string.phrase_category_shopping),
    MEDICAL(R.string.phrase_category_medical),
    EMERGENCY(R.string.phrase_category_emergency),
    WORK(R.string.phrase_category_work)
}

data class PhraseEntry(
    val category: PhraseCategory,
    val zh: String,
    val en: String
)

/**
 * Bundled bilingual phrases for the offline face-to-face scenario. No network
 * or model call — every entry is available immediately.
 */
object Phrasebook {
    val entries: List<PhraseEntry> = buildList {
        // Greetings & basics
        add(PhraseEntry(PhraseCategory.GREETINGS, "你好，很高兴见到你。", "Hello, nice to meet you."))
        add(PhraseEntry(PhraseCategory.GREETINGS, "谢谢你的帮助。", "Thank you for your help."))
        add(PhraseEntry(PhraseCategory.GREETINGS, "对不起，我听不懂。", "Sorry, I don't understand."))
        add(PhraseEntry(PhraseCategory.GREETINGS, "请说慢一点。", "Please speak more slowly."))
        add(PhraseEntry(PhraseCategory.GREETINGS, "你会说中文吗？", "Do you speak Chinese?"))
        add(PhraseEntry(PhraseCategory.GREETINGS, "我用翻译软件和你交流。", "I'm using a translation app to talk with you."))

        // Transport
        add(PhraseEntry(PhraseCategory.TRANSPORT, "去机场多少钱？", "How much is it to the airport?"))
        add(PhraseEntry(PhraseCategory.TRANSPORT, "请带我去这个地址。", "Please take me to this address."))
        add(PhraseEntry(PhraseCategory.TRANSPORT, "附近有加油站吗？", "Is there a gas station nearby?"))
        add(PhraseEntry(PhraseCategory.TRANSPORT, "我需要一辆出租车。", "I need a taxi."))
        add(PhraseEntry(PhraseCategory.TRANSPORT, "请在这里停车。", "Please stop here."))
        add(PhraseEntry(PhraseCategory.TRANSPORT, "到目的地需要多久？", "How long does it take to get there?"))

        // Accommodation
        add(PhraseEntry(PhraseCategory.ACCOMMODATION, "我预订了一个房间。", "I have a room reservation."))
        add(PhraseEntry(PhraseCategory.ACCOMMODATION, "有无线网络吗？", "Is there Wi-Fi?"))
        add(PhraseEntry(PhraseCategory.ACCOMMODATION, "房间的空调坏了。", "The air conditioner in my room is not working."))
        add(PhraseEntry(PhraseCategory.ACCOMMODATION, "我想续住一晚。", "I'd like to stay one more night."))
        add(PhraseEntry(PhraseCategory.ACCOMMODATION, "退房时间是几点？", "What time is check-out?"))
        add(PhraseEntry(PhraseCategory.ACCOMMODATION, "请给我一间安静的房间。", "Please give me a quiet room."))

        // Food
        add(PhraseEntry(PhraseCategory.FOOD, "请给我菜单。", "May I have the menu, please?"))
        add(PhraseEntry(PhraseCategory.FOOD, "我不吃辣。", "I don't eat spicy food."))
        add(PhraseEntry(PhraseCategory.FOOD, "我对花生过敏。", "I'm allergic to peanuts."))
        add(PhraseEntry(PhraseCategory.FOOD, "请给我一杯水。", "Please give me a glass of water."))
        add(PhraseEntry(PhraseCategory.FOOD, "买单，谢谢。", "The bill, please."))
        add(PhraseEntry(PhraseCategory.FOOD, "这道菜是什么？", "What is this dish?"))

        // Shopping & bargaining
        add(PhraseEntry(PhraseCategory.SHOPPING, "这个多少钱？", "How much is this?"))
        add(PhraseEntry(PhraseCategory.SHOPPING, "太贵了，可以便宜一点吗？", "That's too expensive. Can you lower the price?"))
        add(PhraseEntry(PhraseCategory.SHOPPING, "我可以试穿吗？", "May I try it on?"))
        add(PhraseEntry(PhraseCategory.SHOPPING, "我要这个。", "I'll take this one."))
        add(PhraseEntry(PhraseCategory.SHOPPING, "可以刷卡吗？", "Can I pay by card?"))
        add(PhraseEntry(PhraseCategory.SHOPPING, "请给我收据。", "Please give me a receipt."))

        // Medical
        add(PhraseEntry(PhraseCategory.MEDICAL, "我需要看医生。", "I need to see a doctor."))
        add(PhraseEntry(PhraseCategory.MEDICAL, "请叫救护车。", "Please call an ambulance."))
        add(PhraseEntry(PhraseCategory.MEDICAL, "我受伤了。", "I am injured."))
        add(PhraseEntry(PhraseCategory.MEDICAL, "我头很痛。", "I have a bad headache."))
        add(PhraseEntry(PhraseCategory.MEDICAL, "最近的医院在哪里？", "Where is the nearest hospital?"))
        add(PhraseEntry(PhraseCategory.MEDICAL, "我有糖尿病。", "I have diabetes."))

        // Emergency
        add(PhraseEntry(PhraseCategory.EMERGENCY, "请帮帮我！", "Please help me!"))
        add(PhraseEntry(PhraseCategory.EMERGENCY, "我的护照丢了。", "I lost my passport."))
        add(PhraseEntry(PhraseCategory.EMERGENCY, "我的手机被偷了。", "My phone was stolen."))
        add(PhraseEntry(PhraseCategory.EMERGENCY, "请报警。", "Please call the police."))
        add(PhraseEntry(PhraseCategory.EMERGENCY, "我迷路了。", "I am lost."))
        add(PhraseEntry(PhraseCategory.EMERGENCY, "这里安全吗？", "Is it safe here?"))

        // Work
        add(PhraseEntry(PhraseCategory.WORK, "我们开始开会吧。", "Let's start the meeting."))
        add(PhraseEntry(PhraseCategory.WORK, "请再解释一遍。", "Please explain it again."))
        add(PhraseEntry(PhraseCategory.WORK, "我同意这个方案。", "I agree with this plan."))
        add(PhraseEntry(PhraseCategory.WORK, "交货日期是什么时候？", "When is the delivery date?"))
        add(PhraseEntry(PhraseCategory.WORK, "请确认一下数量。", "Please confirm the quantity."))
        add(PhraseEntry(PhraseCategory.WORK, "我们可以签合同了。", "We can sign the contract now."))
    }
}
