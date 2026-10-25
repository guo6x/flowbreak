import { useId } from 'react';
import { Mail } from 'lucide-react';

const SUPPORT_EMAIL = '15015091629@163.com';

/** An explicit user action opens email; no diagnostics or usage data is attached. */
export default function SupportContact() {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className="card p-5 mb-6">
      <div className="flex items-center gap-2 mb-2">
        <Mail size={18} className="text-primary shrink-0" aria-hidden="true" />
        <h2 id={titleId} className="text-[15px] font-semibold">联系与反馈</h2>
      </div>
      <p className="text-[13px] text-gray-600 leading-6">遇到问题或有改进建议，可以主动发邮件联系我们。</p>
      <a href={`mailto:${SUPPORT_EMAIL}`} className="inline-flex items-center min-h-11 max-w-full text-[14px] font-semibold text-primary underline underline-offset-4 break-all">
        {SUPPORT_EMAIL}
      </a>
      <p className="text-[12px] text-gray-600 leading-6">链接不会自动附带诊断或使用记录。来信及附件由邮件服务及收件方处理；请勿发送密码、验证码、身份证件或不必要的完整使用记录。</p>
    </section>
  );
}
