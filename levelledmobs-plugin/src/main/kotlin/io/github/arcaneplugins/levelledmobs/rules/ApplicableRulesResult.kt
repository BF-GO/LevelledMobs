package io.github.arcaneplugins.levelledmobs.rules

/**
 * Сохраняет информацию о результатах при применении пользовательских капель.
 *
 * @author stumper66
 * @since 3.1.2
 */
class ApplicableRulesResult {
    val allApplicableRules = mutableListOf<RuleInfo>()
    val allApplicableRulesMadeChance = mutableListOf<RuleInfo>()
    val allApplicableRulesDidNotMakeChance = mutableListOf<RuleInfo>()
}