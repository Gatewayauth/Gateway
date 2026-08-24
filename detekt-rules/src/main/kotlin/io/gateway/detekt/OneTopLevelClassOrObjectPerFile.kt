package io.gateway.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/**
 * Reports files that declare more than one top-level class or object. Keeping a
 * single top-level type per file makes navigation predictable and diffs small.
 * Top-level functions, properties, and type aliases are allowed.
 */
class OneTopLevelClassOrObjectPerFile(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "OneTopLevelClassOrObjectPerFile",
        severity = Severity.Style,
        description = "A file must not contain more than one top-level class or object.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val topLevelTypes = file.declarations.filterIsInstance<KtClassOrObject>()
        if (topLevelTypes.size > 1) {
            // Flag every declaration after the first.
            topLevelTypes.drop(1).forEach { extra ->
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(extra),
                        message = "File '${file.name}' has ${topLevelTypes.size} top-level classes/objects; " +
                            "split '${extra.name}' into its own file.",
                    ),
                )
            }
        }
    }
}
