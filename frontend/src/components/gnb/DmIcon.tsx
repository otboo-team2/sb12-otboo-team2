import { useEffect, useRef, useState } from 'react';
import { useDmConversationStore } from '@/lib/stores/useDmConversationStore';
import { DmDropdownList } from './DmDropdownList';
import messageIcon from '@/assets/icons/ic_send.svg';

export default function DmIcon() {
    const [isHovered, setIsHovered] = useState(false);
    const [isListOpen, setIsListOpen] = useState(false);
    const iconRef = useRef<HTMLDivElement>(null);
    const { fetch, clear } = useDmConversationStore();

    useEffect(() => {
        fetch();
        return () => clear();
    }, [fetch, clear]);

    const handleClick = () => setIsListOpen((prev) => !prev);
    const handleCloseList = () => setIsListOpen(false);

    return (
        <>
            <div
                ref={iconRef}
                className="relative cursor-pointer"
                onClick={handleClick}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
            >
                <div className={`overflow-clip relative shrink-0 size-6 transition-opacity ${isHovered ? 'opacity-80' : 'opacity-100'}`}>
                    <img alt="메시지" className="block max-w-none size-full" src={messageIcon} />
                </div>
            </div>

            <DmDropdownList
                isOpen={isListOpen}
                onClose={handleCloseList}
                anchorElement={iconRef.current}
            />
        </>
    );
}
