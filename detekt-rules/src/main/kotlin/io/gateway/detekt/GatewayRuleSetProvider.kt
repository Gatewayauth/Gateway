package io.gateway.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** Registers Gateway's custom detekt rules under the `gateway` ruleset id. */
class GatewayRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "gateway"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            OneTopLevelClassOrObjectPerFile(config),
        ),
    )
}
