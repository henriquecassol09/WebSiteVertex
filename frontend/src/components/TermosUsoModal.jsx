import React from 'react';
import { X } from 'lucide-react';

export const TermosUsoModal = ({ isOpen, onClose, onAccept }) => {
  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-[60] flex items-center justify-center p-3 sm:p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-150"
      role="dialog"
      aria-modal="true"
      aria-labelledby="termos-uso-titulo"
    >
      <div className="relative bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal rounded-xl max-w-2xl w-full max-h-[90dvh] flex flex-col overflow-hidden transition-colors duration-200">
        {/* Header */}
        <div className="px-5 sm:px-6 py-4 flex items-center justify-between border-b border-vertex-border dark:border-vertex-dark-border shrink-0">
          <div>
            <h2 id="termos-uso-titulo" className="text-base sm:text-lg font-semibold text-vertex-dark dark:text-vertex-dark-text">
              Termos de Uso
            </h2>
            <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-0.5">
              Última atualização: 22 de outubro de 2026
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-vertex-muted hover:text-vertex-dark dark:text-vertex-dark-muted dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors"
            aria-label="Fechar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Conteúdo dos Termos (Texto padrão de sites e plataformas) */}
        <div className="p-5 sm:p-6 flex-1 overflow-y-auto space-y-5 text-xs sm:text-[13px] text-vertex-body dark:text-vertex-dark-muted leading-relaxed select-text">
          <p className="text-vertex-dark dark:text-vertex-dark-text">
            Bem-vindo ao VERTEX. Ao acessar ou utilizar nossa plataforma, você concorda em cumprir e estar vinculado aos seguintes Termos de Uso. Por favor, leia este documento com atenção antes de concluir seu cadastro.
          </p>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              1. Aceitação dos Termos
            </h3>
            <p>
              Ao criar uma conta ou utilizar os serviços do VERTEX, você confirma que leu, compreendeu e concorda expressamente com estes Termos de Uso e com nossa Política de Privacidade. Caso não concorde com qualquer disposição aqui estabelecida, você não deverá prosseguir com o cadastro ou utilizar a plataforma.
            </p>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              2. Descrição dos Serviços
            </h3>
            <p>
              O VERTEX fornece uma ferramenta de consulta, análise cadastral e inteligência de mercado voltada ao ambiente corporativo (B2B), permitindo a pesquisa e visualização de informações públicas de pessoas jurídicas registradas em bases oficiais brasileiras.
            </p>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              3. Cadastro e Segurança da Conta
            </h3>
            <p>
              Para utilizar as funcionalidades da plataforma, o usuário deve criar uma conta informando dados verdadeiros, completos e atualizados.
            </p>
            <ul className="list-disc pl-5 space-y-1">
              <li>O usuário é o único responsável pela guarda, confidencialidade e uso adequado de suas credenciais de acesso.</li>
              <li>A conta é pessoal e intransferível, sendo vedado o compartilhamento de login ou senhas com terceiros.</li>
              <li>O usuário deve notificar imediatamente o suporte do VERTEX caso identifique qualquer indício de acesso não autorizado à sua conta.</li>
            </ul>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              4. Privacidade e Proteção de Dados (LGPD)
            </h3>
            <p>
              O VERTEX respeita a privacidade de seus usuários e cumpre os preceitos da Lei Geral de Proteção de Dados Pessoais (Lei nº 13.709/2018 - LGPD).
            </p>
            <p>
              Os dados corporativos apresentados decorrem de registros públicos oficiais disponibilizados por órgãos governamentais. O usuário compromete-se a utilizar as informações obtidas exclusivamente para finalidades legítimas, comerciais ou institucionais, abstendo-se de qualquer prática que viole direitos de terceiros.
            </p>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              5. Regras de Conduta e Restrições de Uso
            </h3>
            <p>Ao utilizar o VERTEX, é expressamente proibido ao usuário:</p>
            <ul className="list-disc pl-5 space-y-1">
              <li>Utilizar as informações para envio de comunicações em massa não solicitadas (SPAM) ou práticas abusivas de contato;</li>
              <li>Executar varreduras de vulnerabilidade, ataques de negação de serviço (DoS) ou tentativas de burlar mecanismos de autenticação e limites de requisição;</li>
              <li>Empregar robôs, spiders, crawlers ou outros métodos automatizados para extração maciça de dados sem autorização prévia;</li>
              <li>Realizar engenharia reversa, descompilação ou modificação de qualquer componente do software;</li>
              <li>Utilizar a plataforma para qualquer finalidade ilícita, fraudulenta ou contrária à legislação vigente.</li>
            </ul>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              6. Propriedade Intelectual
            </h3>
            <p>
              A marca VERTEX, logotipos, códigos-fonte, algoritmos, designs e conteúdos proprietários disponibilizados na plataforma são de titularidade exclusiva de seus desenvolvedores, protegidos pelas leis de direito autoral e de propriedade industrial.
            </p>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              7. Limitação de Responsabilidade
            </h3>
            <p>
              A plataforma é fornecida "como está" e "conforme disponível". Embora empregue esforços contínuos para manter a estabilidade do serviço, o VERTEX não garante operação totalmente ininterrupta ou isenta de falhas decorrentes de serviços de telecomunicações ou instabilidades em fontes públicas externas.
            </p>
            <p>
              O VERTEX não responderá por lucros cessantes, perdas indiretas ou decisões comerciais tomadas com base nas informações consultadas na plataforma.
            </p>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              8. Modificações dos Termos
            </h3>
            <p>
              Estes Termos de Uso poderão ser alterados a qualquer tempo para refletir melhorias no sistema ou adequações legais. A continuidade do uso da plataforma após a publicação de novos termos implicará a aceitação tácita das alterações.
            </p>
          </section>

          <section className="space-y-1.5">
            <h3 className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
              9. Legislação Aplicável e Foro
            </h3>
            <p>
              Estes termos são regidos pelas leis da República Federativa do Brasil. Fica eleito o foro da comarca da sede de operação do VERTEX para dirimir eventuais controvérsias decorrentes deste contrato, com renúncia expressa a qualquer outro.
            </p>
          </section>
        </div>

        {/* Footer com Ações */}
        <div className="px-5 sm:px-6 py-3.5 border-t border-vertex-border dark:border-vertex-dark-border bg-slate-50 dark:bg-vertex-dark-surface flex items-center justify-end gap-2.5 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 min-h-[36px] rounded-lg border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-100 dark:hover:bg-vertex-dark-border/40 text-xs font-medium text-vertex-dark dark:text-vertex-dark-text transition-colors"
          >
            Fechar
          </button>

          {onAccept && (
            <button
              type="button"
              onClick={() => {
                onAccept();
                onClose();
              }}
              className="px-4 py-2 min-h-[36px] rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover text-white text-xs font-medium transition-colors shadow-xs"
            >
              Concordar com os Termos
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
